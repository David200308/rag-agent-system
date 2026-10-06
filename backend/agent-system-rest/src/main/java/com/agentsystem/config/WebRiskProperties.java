package com.agentsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for Google Web Risk URL reputation checks (blocks known phishing /
 * malware / unwanted-software pages before fetchWebPage loads or returns them).
 *
 * Lookup API free tier: 100k uris.search calls/month — verdicts are cached
 * Caffeine → Redis → MySQL so repeated URLs never hit Google twice within their TTL.
 * Inactive (every URL treated as unchecked/allowed) until an API key is configured.
 *
 * Properties:
 *   web-risk.enabled              — master switch (default: true; still needs api-key)
 *   web-risk.api-key              — Google Cloud API key restricted to the Web Risk API
 *   web-risk.timeout-ms           — Google call timeout; on timeout/error the fetch is allowed (default: 2000)
 *   web-risk.safe-ttl-minutes     — how long a clean verdict is trusted (default: 60)
 *   web-risk.unsafe-ttl-hours     — fallback TTL for a threat verdict when Google sends no expireTime (default: 24)
 *   web-risk.local-cache-max-size — Caffeine (per-instance) entry cap (default: 10000)
 */
@ConfigurationProperties(prefix = "web-risk")
public record WebRiskProperties(
        boolean enabled,
        String apiKey,
        int timeoutMs,
        int safeTtlMinutes,
        int unsafeTtlHours,
        int localCacheMaxSize
) {
    public WebRiskProperties {
        if (timeoutMs <= 0)          timeoutMs         = 2000;
        if (safeTtlMinutes <= 0)     safeTtlMinutes    = 60;
        if (unsafeTtlHours <= 0)     unsafeTtlHours    = 24;
        if (localCacheMaxSize <= 0)  localCacheMaxSize = 10_000;
    }

    /** Checks run only when switched on AND a key is configured. */
    public boolean active() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
