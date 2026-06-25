package com.agentsystem.auth.service;

public interface AuthService {

    /**
     * Validates the email against the whitelist, generates a 6-digit OTP,
     * stores it in Redis, and dispatches it via agent-system-notification-inner.
     *
     * @throws IllegalArgumentException if the email is not whitelisted
     */
    void requestOtp(String email);

    /**
     * Validates the OTP and returns a signed JWT on success (personal mode).
     *
     * @throws IllegalArgumentException if the code is wrong or expired
     */
    String verifyOtp(String email, String code);

    /**
     * Validates the OTP and returns a signed JWT on success with explicit mode/org.
     * For TEAM mode, validates that the org exists and the email is a member.
     *
     * @throws IllegalArgumentException if the code is wrong, expired, or org/membership invalid
     */
    String verifyOtp(String email, String code, String mode, String orgId);

    /** Returns the email from the JWT if valid, or {@code null} if invalid/expired. */
    String validateToken(String token);

    /** Returns full claims from the JWT, or {@code null} if invalid/expired. */
    JwtService.TokenClaims validateTokenFull(String token);
}
