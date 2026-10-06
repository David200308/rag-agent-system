package com.agentsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the web-fetch feature (reading a specific URL's rendered content).
 *
 * Backed by a self-hosted Lightpanda headless browser driven over CDP — see
 * docker-compose.yml's {@code lightpanda} service. Lightpanda runs with
 * {@code --block-private-networks}, so SSRF protection is enforced at socket-open
 * time inside the browser (covers redirects, JS sub-requests and DNS rebinding).
 *
 * Properties:
 *   web-fetch.enabled               — master switch (default: true)
 *   web-fetch.cdp-url               — Lightpanda CDP WebSocket endpoint (default: ws://lightpanda:9222)
 *   web-fetch.timeout-seconds       — overall budget per fetch: connect + navigate + load (default: 20)
 *   web-fetch.network-idle-wait-ms  — extra wait after load for XHR-driven content to settle (default: 3000)
 *   web-fetch.max-content-chars     — page Markdown returned to the LLM is truncated to this (default: 20000)
 *   web-fetch.max-concurrent        — concurrent fetches allowed against Lightpanda (default: 4)
 */
@ConfigurationProperties(prefix = "web-fetch")
public record WebFetchProperties(
        boolean enabled,
        String cdpUrl,
        int timeoutSeconds,
        int networkIdleWaitMs,
        int maxContentChars,
        int maxConcurrent
) {
    public WebFetchProperties {
        if (cdpUrl == null || cdpUrl.isBlank()) cdpUrl = "ws://lightpanda:9222";
        if (timeoutSeconds <= 0)    timeoutSeconds    = 20;
        if (networkIdleWaitMs < 0)  networkIdleWaitMs = 3000;
        if (maxContentChars <= 0)   maxContentChars   = 20_000;
        if (maxConcurrent <= 0)     maxConcurrent     = 4;
    }
}
