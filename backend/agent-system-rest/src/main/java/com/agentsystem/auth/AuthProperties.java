package com.ragagent.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        boolean enabled,
        int otpExpiryMinutes,
        String jwtSecret,
        int jwtExpiryHours
) {}
