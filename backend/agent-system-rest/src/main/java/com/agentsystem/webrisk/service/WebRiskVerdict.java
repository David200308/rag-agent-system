package com.agentsystem.webrisk.service;

import java.time.Instant;
import java.util.List;

/**
 * Result of a Web Risk check for one URL.
 *
 * @param safe        no threat matched (also true for {@link #unchecked()} — checks fail open)
 * @param threatTypes matched Web Risk threat types, e.g. MALWARE, SOCIAL_ENGINEERING; empty when safe
 * @param expiresAt   when this verdict must be re-checked; {@code null} for an unchecked verdict (never cached)
 */
public record WebRiskVerdict(boolean safe, List<String> threatTypes, Instant expiresAt) {

    public WebRiskVerdict {
        threatTypes = threatTypes == null ? List.of() : List.copyOf(threatTypes);
    }

    /** Checks disabled, or Google unreachable — allow, but don't cache. */
    public static WebRiskVerdict unchecked() {
        return new WebRiskVerdict(true, List.of(), null);
    }

    public boolean isExpired(Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }
}
