package com.agentsystem.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from:
 *   auth.jwt-secret          (env: AUTH_JWT_SECRET)
 *   auth.jwt-expiry-hours    (env: AUTH_JWT_EXPIRY_HOURS)
 *   auth.otp-expiry-minutes  (env: AUTH_OTP_EXPIRY_MINUTES)
 *   auth.service-key         (env: AUTH_SERVICE_KEY) — shared secret expected on X-Auth-Key
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        String jwtSecret,
        int    jwtExpiryHours,
        int    otpExpiryMinutes,
        String serviceKey
) {}
