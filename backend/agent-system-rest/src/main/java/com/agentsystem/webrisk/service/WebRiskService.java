package com.agentsystem.webrisk.service;

public interface WebRiskService {

    /**
     * Checks {@code url} against Google Web Risk (MALWARE, SOCIAL_ENGINEERING,
     * UNWANTED_SOFTWARE). Never throws: when checks are disabled or Google is
     * unreachable, returns {@link WebRiskVerdict#unchecked()} (fail open).
     */
    WebRiskVerdict check(String url);
}
