package com.ragagent.agent.nodes;

import com.ragagent.agent.state.AgentState;
import com.ragagent.fallback.FallbackService;
import com.ragagent.schema.AgentRequest;
import com.ragagent.schema.AgentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Node 4 — Fallback.
 *
 * Activated when:
 *   - The query analyser routes to FALLBACK (ambiguous / out-of-scope)
 *   - The retrieval node finds no relevant documents
 *   - The LLM circuit-breaker is open (handled in {@link FallbackService})
 *
 * Strategy hierarchy (applied in order until one succeeds):
 *   1. Cached answer (recent identical/similar query)
 *   2. Default knowledge answer (no retrieval, LLM only)
 *   3. Static "cannot answer" response
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FallbackNode {

    private final FallbackService fallbackService;

    public Map<String, Object> process(AgentState state) {
        AgentRequest request = state.request().orElseThrow();
        String reason = state.fallbackReason().orElse("Unknown reason");

        log.warn("[FallbackNode] Activating fallback — reason: {}", reason);

        String answer = fallbackService.resolveFallback(request.query(), reason);

        AgentResponse response = new AgentResponse(
                answer,
                List.of(),
                new AgentResponse.RouteDecision("FALLBACK", reason, 0.0),
                true,
                reason,
                new AgentResponse.RunMetadata(
                        state.runId().orElse(UUID.randomUUID().toString()),
                        Instant.now(),
                        0L,
                        0,
                        "fallback",
                        null   // conversationId injected by AgentController after persistence
                )
        );

        return Map.of("response", response);
    }
}
