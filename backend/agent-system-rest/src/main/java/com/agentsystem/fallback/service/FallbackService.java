package com.agentsystem.fallback.service;

import java.util.Optional;

public interface FallbackService {

    /**
     * Entry point called by {@link com.agentsystem.agent.nodes.FallbackNode}.
     */
    String resolveFallback(String query, String reason, Optional<String> selectedModelDisplayName);

    /** Cache a known good answer manually (e.g. from admin endpoint). */
    void cacheAnswer(String query, String answer);
}
