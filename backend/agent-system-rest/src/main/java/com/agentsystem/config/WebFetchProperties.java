package com.ragagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the web-fetch feature.
 *
 * Properties:
 *   web-fetch.enabled                  — master switch (default: true)
 *   web-fetch.timeout-seconds          — HTTP connect/read timeout (default: 10)
 *   web-fetch.max-content-length-chars — truncate extracted text beyond this limit (default: 50000)
 *
 * The domain whitelist is managed entirely via the database
 * (POST/DELETE /api/v1/agent/web-fetch/whitelist).
 */
@ConfigurationProperties(prefix = "web-fetch")
public record WebFetchProperties(
        boolean enabled,
        int timeoutSeconds,
        int maxContentLengthChars
) {
    public WebFetchProperties {
        if (timeoutSeconds <= 0)        timeoutSeconds        = 10;
        if (maxContentLengthChars <= 0) maxContentLengthChars = 50_000;
    }
}
