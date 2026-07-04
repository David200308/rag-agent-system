package com.agentsystem.user.service;

import com.agentsystem.user.entity.User;

import java.util.Optional;

public interface UserAccountService {

    /**
     * Registers a newly-verified email, or returns the existing row unchanged if one
     * already exists (idempotent — never regresses an approved USER back to PRE_USER).
     */
    User registerOrGetPending(String email);

    /** A user that is allowed to log in: exists, status=USER, enabled=true. */
    Optional<User> findActiveUser(String email);

    /** Any user row for this email regardless of status/enabled — used for UX messaging. */
    Optional<User> findByEmail(String email);

    /**
     * Resolves a user's plaintext email from their uuid, via a Redis cache (3h TTL) in
     * front of the encrypted DB column — avoids re-decrypting on every authenticated
     * request.
     */
    String getEmailByUuid(String uuid);
}
