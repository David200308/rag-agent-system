package com.ragagent.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for client identity enforcement.
 *
 * Properties:
 *   client.identity.enabled    — master toggle (false = all requests pass through)
 *   client.identity.ios-secret — HMAC-SHA256 shared secret for iOS app
 *   client.identity.web-secret — HMAC-SHA256 shared secret for web (Next.js server-side)
 *
 * CLI clients use Ed25519 per-user keys registered via /api/v1/auth/register-key.
 */
@ConfigurationProperties("client.identity")
public record ClientIdentityProperties(
        boolean enabled,
        String  iosSecret,
        String  webSecret
) {}
