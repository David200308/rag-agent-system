package com.ragagent.agent.nodes;

import com.ragagent.agent.state.AgentState;
import com.ragagent.knowledge.KnowledgeSourceService;
import com.ragagent.rag.RetrievalService;
import com.ragagent.schema.AgentRequest;
import com.ragagent.schema.DocumentResult;
import com.ragagent.schema.QueryAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Node 2 — Retrieval.
 *
 * Queries Weaviate through {@link RetrievalService} using the refined query
 * produced by {@link QueryAnalyzerNode}.  Resilience4j circuit-breaker and
 * retry logic live inside {@link RetrievalService}.
 *
 * Routes after completion:
 *   documents found  → "generate"
 *   no documents     → "fallback"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrievalNode {

    private final RetrievalService     retrievalService;
    private final KnowledgeSourceService knowledgeSourceService;

    public Map<String, Object> process(AgentState state) {
        AgentRequest  request  = state.request().orElseThrow();
        QueryAnalysis analysis = state.queryAnalysis().orElseThrow();

        // Safety guard: knowledge base search was disabled per-request.
        // WebFetchNode should have already rerouted to DIRECT, but guard here too.
        if (!request.isKnowledgeBaseEnabled()) {
            log.info("[RetrievalNode] Knowledge base disabled for this request — skipping");
            return Map.of();
        }

        String query     = analysis.refinedQuery();
        int    topK      = request.effectiveTopK();
        String userEmail = state.userEmail().orElse(null);

        // Resolve the set of source IDs this caller is allowed to read.
        // null means auth is disabled — no source restriction applied.
        Set<String> allowedSources = resolveAllowedSources(userEmail);

        log.debug("[RetrievalNode] Retrieving top-{} docs for: {} (user={}, allowedSources={})",
                topK, query, userEmail, allowedSources == null ? "unrestricted" : allowedSources.size());

        List<DocumentResult> docs = retrievalService.retrieve(
                query, topK, request.filters(), allowedSources);

        log.info("[RetrievalNode] Retrieved {} documents", docs.size());

        if (docs.isEmpty()) {
            return Map.of(
                    "route",         "FALLBACK",
                    "fallbackReason", "No relevant documents found in the knowledge base"
            );
        }

        return Map.of("documents", docs);
    }

    /**
     * Returns the set of source IDs the caller may read, or {@code null} when auth is
     * disabled (no restriction). An empty set means the user has no accessible sources.
     */
    private Set<String> resolveAllowedSources(String userEmail) {
        if (userEmail == null) {
            // Auth is disabled — allow unrestricted access (null = no filter).
            return null;
        }
        return knowledgeSourceService.listAccessible(userEmail).stream()
                .map(ks -> ks.getSource())
                .collect(Collectors.toSet());
    }
}
