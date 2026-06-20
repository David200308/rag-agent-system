package com.ragagent.auth.service;

import com.ragagent.auth.AuthProperties;
import com.ragagent.auth.repository.EmailWhitelistRepository;
import com.ragagent.org.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * OTP codes are stored in Redis under {@code otp:<email>} with the configured expiry as
 * the key's native TTL — no separate "used" flag or cleanup job needed: issuing a new code
 * overwrites the key (SETEX), and verifying deletes it, so a code can't be reused or outlive
 * its TTL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthProperties           authProperties;
    private final EmailWhitelistRepository whitelistRepo;
    private final StringRedisTemplate      redisTemplate;
    private final EmailService             emailService;
    private final JwtService               jwtService;
    private final OrganizationService      orgService;

    private final SecureRandom random = new SecureRandom();

    private static final String OTP_KEY_PREFIX = "otp:";

    // ── Request OTP ──────────────────────────────────────────────────────────────

    /**
     * Validates the email against the whitelist, generates a 6-digit OTP,
     * stores it in Redis, and sends it via Resend.
     *
     * @throws IllegalArgumentException if the email is not whitelisted
     */
    public void requestOtp(String email) {
        String normalised = email.trim().toLowerCase();

        whitelistRepo.findByEmailIgnoreCaseAndEnabledTrue(normalised)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Email is not authorised to access this system."));

        String code   = generateCode();
        int    expiry = authProperties.otpExpiryMinutes();

        // SETEX overwrites any previous code for this email, naturally invalidating it.
        redisTemplate.opsForValue().set(OTP_KEY_PREFIX + normalised, code, Duration.ofMinutes(expiry));

        emailService.sendOtp(normalised, code, expiry);
        log.info("[AuthService] OTP issued for {}", normalised);
    }

    // ── Verify OTP ───────────────────────────────────────────────────────────────

    /**
     * Validates the OTP and returns a signed JWT on success (personal mode).
     *
     * @throws IllegalArgumentException if the code is wrong or expired
     */
    public String verifyOtp(String email, String code) {
        return verifyOtp(email, code, "PERSONAL", null);
    }

    /**
     * Validates the OTP and returns a signed JWT on success with explicit mode/org.
     * For TEAM mode, validates that the org exists and the email is a member.
     *
     * @throws IllegalArgumentException if the code is wrong, expired, or org/membership invalid
     */
    public String verifyOtp(String email, String code, String mode, String orgId) {
        String normalised = email.trim().toLowerCase();
        String key         = OTP_KEY_PREFIX + normalised;

        String storedCode = redisTemplate.opsForValue().get(key);
        if (storedCode == null || !storedCode.equals(code.trim())) {
            throw new IllegalArgumentException("Invalid or expired code.");
        }

        if ("TEAM".equals(mode)) {
            if (orgId == null || orgId.isBlank()) {
                throw new IllegalArgumentException("orgId is required for team mode.");
            }
            if (!orgService.isMember(orgId.trim(), normalised)) {
                throw new IllegalArgumentException(
                        "You are not a member of organization: " + orgId.trim());
            }
        }

        redisTemplate.delete(key);

        String resolvedOrgId = "TEAM".equals(mode) ? orgId.trim() : null;
        String jwt = jwtService.generate(normalised, mode, resolvedOrgId);
        log.info("[AuthService] JWT issued for {} (mode={}, org={})", normalised, mode, resolvedOrgId);
        return jwt;
    }

    // ── Validate JWT ─────────────────────────────────────────────────────────────

    /** Returns the email from the JWT if valid, or {@code null} if invalid/expired. */
    public String validateToken(String token) {
        return jwtService.validate(token);
    }

    /** Returns full claims from the JWT, or {@code null} if invalid/expired. */
    public JwtService.TokenClaims validateTokenFull(String token) {
        return jwtService.validateFull(token);
    }

    // ── Helper ───────────────────────────────────────────────────────────────────

    private String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
