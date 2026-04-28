package com.ragagent.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound to the {@code auth.*} block in application.yml.
 *
 * auth:
 *   enabled: true                    # set false to bypass login entirely
 *   otp-expiry-minutes: 10
 *   jwt-secret: <long-random-base64> # ≥ 256-bit recommended
 *   jwt-expiry-hours: 24
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        boolean enabled,
        int otpExpiryMinutes,
        String jwtSecret,
        int jwtExpiryHours,
        String gatewayKey   // shared static secret for agent-openapi; null = disabled
) {}
