package com.agentsystem.auth.service.impl;

import com.agentsystem.auth.service.AuthService;
import com.agentsystem.auth.service.JwtService;

import com.agentsystem.auth.AuthProperties;
import com.agentsystem.notification.NotificationClient;
import com.agentsystem.org.service.OrganizationService;
import com.agentsystem.user.entity.User;
import com.agentsystem.user.entity.UserStatus;
import com.agentsystem.user.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;

/**
 * OTP codes are stored in Redis under {@code otp:<email>} (login) or
 * {@code register-otp:<email>} (registration) with the configured expiry as the key's
 * native TTL — no separate "used" flag or cleanup job needed: issuing a new code
 * overwrites the key (SETEX), and verifying deletes it, so a code can't be reused or outlive
 * its TTL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthProperties      authProperties;
    private final UserAccountService  userAccountService;
    private final StringRedisTemplate redisTemplate;
    private final NotificationClient  notificationClient;
    private final JwtService          jwtService;
    private final OrganizationService orgService;

    private final SecureRandom random = new SecureRandom();

    private static final String OTP_KEY_PREFIX               = "otp:";
    private static final String OTP_ATTEMPTS_KEY_PREFIX       = "otp:attempts:";
    private static final String REGISTER_OTP_KEY_PREFIX       = "register-otp:";
    private static final String REGISTER_OTP_ATTEMPTS_PREFIX  = "register-otp:attempts:";
    private static final int    MAX_OTP_ATTEMPTS               = 5;

    // ── Request OTP (login) ──────────────────────────────────────────────────────

    /**
     * Validates the email against the users table, generates a 6-digit OTP,
     * stores it in Redis, and dispatches it via agent-system-notification-inner.
     *
     * @throws IllegalArgumentException if the email isn't an approved, enabled user
     */
    @Override
    public void requestOtp(String email) {
        String normalised = email.trim().toLowerCase();

        if (userAccountService.findActiveUser(normalised).isEmpty()) {
            User existing = userAccountService.findByEmail(normalised).orElse(null);
            if (existing != null && existing.getStatus() == UserStatus.PRE_USER) {
                throw new IllegalArgumentException("Your registration is pending approval.");
            }
            throw new IllegalArgumentException("Please register first.");
        }

        sendOtp(OTP_KEY_PREFIX, OTP_ATTEMPTS_KEY_PREFIX, normalised);
        log.info("[AuthService] OTP issued for {}", normalised);
    }

    // ── Verify OTP (login) ───────────────────────────────────────────────────────

    /**
     * Validates the OTP and returns a signed JWT on success (personal mode).
     *
     * @throws IllegalArgumentException if the code is wrong or expired
     */
    @Override
    public String verifyOtp(String email, String code) {
        return verifyOtp(email, code, "PERSONAL", null);
    }

    /**
     * Validates the OTP and returns a signed JWT on success with explicit mode/org.
     * For TEAM mode, validates that the org exists and the email is a member.
     *
     * @throws IllegalArgumentException if the code is wrong, expired, or org/membership invalid
     */
    @Override
    public String verifyOtp(String email, String code, String mode, String orgId) {
        String normalised = email.trim().toLowerCase();

        verifyCode(OTP_KEY_PREFIX, OTP_ATTEMPTS_KEY_PREFIX, normalised, code);

        if ("TEAM".equals(mode)) {
            if (orgId == null || orgId.isBlank()) {
                throw new IllegalArgumentException("orgId is required for team mode.");
            }
            if (!orgService.isMember(orgId.trim(), normalised)) {
                throw new IllegalArgumentException(
                        "You are not a member of organization: " + orgId.trim());
            }
        }

        // Re-check status/enabled in case it changed between request and verify.
        User user = userAccountService.findActiveUser(normalised)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Your account is no longer active. Contact an admin."));

        String resolvedOrgId = "TEAM".equals(mode) ? orgId.trim() : null;
        String jwt = jwtService.generate(user.getUuid(), mode, resolvedOrgId);
        log.info("[AuthService] JWT issued for {} (mode={}, org={})", normalised, mode, resolvedOrgId);
        return jwt;
    }

    // ── Registration OTP ─────────────────────────────────────────────────────────

    /**
     * Sends a 6-digit OTP to prove ownership of {@code email} for registration. Unlike
     * {@link #requestOtp}, this has no gate — anyone can attempt to register. No row is
     * created until the code is verified, under a distinct Redis key prefix so a
     * registration code can't be reused to authenticate an existing session (or vice versa).
     */
    @Override
    public void requestRegistrationOtp(String email) {
        String normalised = email.trim().toLowerCase();
        sendOtp(REGISTER_OTP_KEY_PREFIX, REGISTER_OTP_ATTEMPTS_PREFIX, normalised);
        log.info("[AuthService] Registration OTP issued for {}", normalised);
    }

    /**
     * Validates the registration OTP and, on success, creates a PRE_USER row for this
     * email (or returns the existing status if it's already registered/approved).
     *
     * @throws IllegalArgumentException if the code is wrong or expired
     */
    @Override
    public UserStatus verifyRegistrationOtp(String email, String code) {
        String normalised = email.trim().toLowerCase();
        verifyCode(REGISTER_OTP_KEY_PREFIX, REGISTER_OTP_ATTEMPTS_PREFIX, normalised, code);

        User user = userAccountService.registerOrGetPending(normalised);
        log.info("[AuthService] Registration verified for {} (status={})", normalised, user.getStatus());
        return user.getStatus();
    }

    // ── Validate JWT ─────────────────────────────────────────────────────────────

    /** Returns the user's email from the JWT if valid, or {@code null} if invalid/expired. */
    @Override
    public String validateToken(String token) {
        JwtService.TokenClaims claims = jwtService.validateFull(token);
        return claims != null ? userAccountService.getEmailByUuid(claims.userUuid()) : null;
    }

    /** Returns full claims from the JWT, or {@code null} if invalid/expired. */
    @Override
    public JwtService.TokenClaims validateTokenFull(String token) {
        return jwtService.validateFull(token);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /**
     * Generates a 6-digit code, stores it in Redis under {@code keyPrefix + email}
     * (resetting any existing attempt-lockout counter), and dispatches it by email.
     */
    private void sendOtp(String keyPrefix, String attemptsPrefix, String normalisedEmail) {
        String code   = generateCode();
        int    expiry = authProperties.otpExpiryMinutes();

        // SETEX overwrites any previous code for this email, naturally invalidating it.
        redisTemplate.opsForValue().set(keyPrefix + normalisedEmail, code, Duration.ofMinutes(expiry));
        // Issuing a fresh code resets the lockout — a legitimate user who got locked
        // out can always recover by requesting a new code (rate-limited separately
        // by RateLimitFilter's OTP/REGISTER_OTP buckets).
        redisTemplate.delete(attemptsPrefix + normalisedEmail);

        notificationClient.sendOtp(normalisedEmail, code, expiry);
    }

    /**
     * Validates a code against the stored OTP for {@code normalisedEmail}, enforcing the
     * attempt lockout. Deletes the code (and attempt counter) once used successfully.
     *
     * @throws IllegalArgumentException if the code is wrong, expired, or attempts exhausted
     */
    private void verifyCode(String keyPrefix, String attemptsPrefix, String normalisedEmail, String code) {
        String key         = keyPrefix + normalisedEmail;
        String attemptsKey = attemptsPrefix + normalisedEmail;

        // Lock out after MAX_OTP_ATTEMPTS regardless of whether this attempt turns out
        // correct — without this, a 6-digit OTP is brute-forceable within its TTL window.
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(attemptsKey, Duration.ofMinutes(authProperties.otpExpiryMinutes()));
        }
        if (attempts != null && attempts > MAX_OTP_ATTEMPTS) {
            throw new IllegalArgumentException("Too many incorrect attempts. Request a new code.");
        }

        String storedCode = redisTemplate.opsForValue().get(key);
        if (storedCode == null || !constantTimeEquals(storedCode, code.trim())) {
            throw new IllegalArgumentException("Invalid or expired code.");
        }

        redisTemplate.delete(key);
        redisTemplate.delete(attemptsKey);
    }

    private String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
