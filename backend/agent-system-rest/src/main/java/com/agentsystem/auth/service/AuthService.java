package com.agentsystem.auth.service;

import com.agentsystem.user.entity.UserStatus;

public interface AuthService {

    /**
     * Validates the email against the users table (must be status=USER and enabled),
     * generates a 6-digit OTP, stores it in Redis, and dispatches it via
     * agent-system-notification-inner.
     *
     * @throws IllegalArgumentException if the email isn't an approved, enabled user
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

    /**
     * Sends a 6-digit OTP to prove ownership of {@code email} for registration. Unlike
     * {@link #requestOtp}, this has no whitelist/user-table gate — anyone can attempt to
     * register. No row is created until the code is verified.
     */
    void requestRegistrationOtp(String email);

    /**
     * Validates the registration OTP and, on success, creates a PRE_USER row for this
     * email (or returns the existing status if it's already registered/approved).
     * Does not issue a JWT — PRE_USER cannot log in until manually approved.
     *
     * @throws IllegalArgumentException if the code is wrong or expired
     */
    UserStatus verifyRegistrationOtp(String email, String code);

    /** Returns the user's email from the JWT if valid, or {@code null} if invalid/expired. */
    String validateToken(String token);

    /** Returns full claims from the JWT, or {@code null} if invalid/expired. */
    JwtService.TokenClaims validateTokenFull(String token);
}
