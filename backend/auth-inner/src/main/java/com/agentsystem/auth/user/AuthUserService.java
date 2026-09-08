package com.agentsystem.auth.user;

import java.util.Optional;

/**
 * Login-flow-scoped subset of agent-system-rest's UserAccountService — see AuthUser
 * for why this exists as its own thin mirror instead of the full user/ package moving.
 */
public interface AuthUserService {

    /** Registers a newly-verified email, or returns the existing row unchanged if one already exists. */
    AuthUser registerOrGetPending(String email);

    /** A user that is allowed to log in: exists, status=USER, enabled=true. */
    Optional<AuthUser> findActiveUser(String email);

    /** Any user row for this email regardless of status/enabled — used for UX messaging. */
    Optional<AuthUser> findByEmail(String email);

    /** Resolves a user's email from their uuid, via a Redis cache (3h TTL) in front of the DB. */
    String getEmailByUuid(String uuid);
}
