package com.ragagent.auth.service;

import com.ragagent.auth.AuthProperties;
import com.ragagent.auth.entity.OtpCode;
import com.ragagent.auth.repository.EmailWhitelistRepository;
import com.ragagent.auth.repository.OtpCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthProperties           authProperties;
    private final EmailWhitelistRepository whitelistRepo;
    private final OtpCodeRepository        otpRepo;
    private final EmailService             emailService;
    private final JwtService               jwtService;

    private final SecureRandom random = new SecureRandom();

    // ── Request OTP ──────────────────────────────────────────────────────────────

    /**
     * Validates the email against the whitelist, generates a 6-digit OTP,
     * persists it, and sends it via Resend.
     *
     * @throws IllegalArgumentException if the email is not whitelisted
     */
    @Transactional
    public void requestOtp(String email) {
        String normalised = email.trim().toLowerCase();

        whitelistRepo.findByEmailIgnoreCaseAndEnabledTrue(normalised)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Email is not authorised to access this system."));

        String        code    = generateCode();
        int           expiry  = authProperties.otpExpiryMinutes();
        LocalDateTime expires = LocalDateTime.now().plusMinutes(expiry);

        // Invalidate any previous codes for this email before issuing a new one
        otpRepo.deleteAllByEmail(normalised);
        otpRepo.save(new OtpCode(normalised, code, expires));

        emailService.sendOtp(normalised, code, expiry);
        log.info("[AuthService] OTP issued for {}", normalised);
    }

    // ── Verify OTP ───────────────────────────────────────────────────────────────

    /**
     * Validates the OTP and returns a signed JWT on success.
     *
     * @throws IllegalArgumentException if the code is wrong or expired
     */
    @Transactional
    public String verifyOtp(String email, String code) {
        String normalised = email.trim().toLowerCase();

        OtpCode otp = otpRepo.findValidOtp(normalised, LocalDateTime.now())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid or expired code."));

        if (!otp.getCode().equals(code.trim())) {
            throw new IllegalArgumentException("Invalid or expired code.");
        }

        // Mark used and clean up remaining codes
        otp.setUsed(true);
        otpRepo.save(otp);
        otpRepo.deleteAllByEmail(normalised);

        String jwt = jwtService.generate(normalised);
        log.info("[AuthService] JWT issued for {}", normalised);
        return jwt;
    }

    // ── Validate JWT ─────────────────────────────────────────────────────────────

    /**
     * Returns the email from the JWT if valid, or {@code null} if invalid/expired.
     */
    public String validateToken(String token) {
        return jwtService.validate(token);
    }

    // ── Scheduled cleanup ────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 3_600_000) // every hour
    @Transactional
    public void cleanupExpired() {
        otpRepo.deleteExpired(LocalDateTime.now());
        log.debug("[AuthService] Expired OTPs purged");
    }

    // ── Helper ───────────────────────────────────────────────────────────────────

    private String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
