package com.ragagent.connector;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "connectors")
public record ConnectorProperties(
        Google    google,
        Figma     figma,
        Telegram  telegram,
        String    callbackBaseUrl
) {
    public record Google(String clientId, String clientSecret) {}
    public record Figma (String clientId, String clientSecret) {}
    public record Telegram(String botToken, String botUsername) {}
}
