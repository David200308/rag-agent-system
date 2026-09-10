package com.agentsystem.fallback.service;

import com.agentsystem.schema.AgentRequest;

import java.util.List;
import java.util.Optional;

public interface FallbackService {

    /**
     * Entry point called by {@link com.agentsystem.agent.nodes.FallbackNode}.
     *
     * @param history prior turns of this conversation, so a fallback answer can still be
     *                context-aware instead of treating every fallback query as a fresh chat
     */
    String resolveFallback(String query, String reason, Optional<String> selectedModelDisplayName,
                            List<AgentRequest.ConversationTurn> history);

    /** Cache a known good answer manually (e.g. from admin endpoint). */
    void cacheAnswer(String query, String answer);
}
