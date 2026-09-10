package com.agentsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the web-search feature (real, open-ended web search — not to be
 * confused with web-fetch, which only fetches specific already-known URLs).
 *
 * Backed by a self-hosted SearXNG instance (free, no API key, no query cap) — see
 * docker-compose.yml's {@code searxng} service and searxng/settings.yml.
 *
 * Properties:
 *   web-search.enabled      — master switch (default: true)
 *   web-search.base-url     — SearXNG instance base URL (default: http://searxng:8080)
 *   web-search.timeout-seconds — HTTP timeout (default: 10)
 *   web-search.max-results  — max results returned to the LLM per query (default: 5)
 */
@ConfigurationProperties(prefix = "web-search")
public record WebSearchProperties(
        boolean enabled,
        String baseUrl,
        int timeoutSeconds,
        int maxResults
) {
    public WebSearchProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://searxng:8080";
        if (timeoutSeconds <= 0) timeoutSeconds = 10;
        if (maxResults <= 0)     maxResults     = 5;
    }
}
