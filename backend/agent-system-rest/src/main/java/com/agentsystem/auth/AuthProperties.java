package com.agentsystem.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT secret, OTP expiry, and passkey RP config all moved to auth-inner's own
 * AuthProperties — this only keeps the local on/off switch AuthFilter checks before
 * deciding whether to call auth-inner at all.
 *
 * Bound from:
 *   auth.enabled  (env: AUTH_ENABLED)
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(boolean enabled) {}
