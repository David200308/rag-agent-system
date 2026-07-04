package com.agentsystem.auth.service;

public interface JwtService {

    record TokenClaims(String userUuid, String mode, String orgId) {}

    /** Create a signed JWT for personal mode. */
    String generate(String userUuid);

    /** Create a signed JWT with explicit mode and optional orgId. */
    String generate(String userUuid, String mode, String orgId);

    /**
     * Validate a token and return its claims, or {@code null} if invalid/expired.
     */
    TokenClaims validateFull(String token);

    /** Backward-compatible: returns the user_uuid subject only. */
    String validate(String token);
}
