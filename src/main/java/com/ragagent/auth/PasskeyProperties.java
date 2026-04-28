package com.ragagent.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.passkey")
public record PasskeyProperties(
        String rpId,
        String rpName,
        String origin
) {}
