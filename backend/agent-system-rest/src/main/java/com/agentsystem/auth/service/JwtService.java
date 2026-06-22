package com.agentsystem.auth.service;

public interface JwtService {

    record TokenClaims(String email, String mode, String orgId) {}

    /** Create a signed JWT for personal mode. */
    String generate(String email);

    /** Create a signed JWT with explicit mode and optional orgId. */
    String generate(String email, String mode, String orgId);

    /**
     * Validate a token and return its claims, or {@code null} if invalid/expired.
     */
    TokenClaims validateFull(String token);

    /** Backward-compatible: returns email only (used by passkey path and register-key). */
    String validate(String token);
}
