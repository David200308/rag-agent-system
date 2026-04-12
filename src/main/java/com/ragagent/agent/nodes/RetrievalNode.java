package com.ragagent.agent.nodes;

import com.ragagent.agent.state.AgentState;
import com.ragagent.rag.RetrievalService;
import com.ragagent.schema.AgentRequest;
import com.ragagent.schema.DocumentResult;
import com.ragagent.schema.QueryAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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

    private final RetrievalService retrievalService;

    public Map<String, Object> process(AgentState state) {
        AgentRequest  request  = state.request().orElseThrow();
        QueryAnalysis analysis = state.queryAnalysis().orElseThrow();

        String query = analysis.refinedQuery();
        int    topK  = request.effectiveTopK();

        log.debug("[RetrievalNode] Retrieving top-{} docs for: {}", topK, query);

        List<DocumentResult> docs = retrievalService.retrieve(
                query, topK, request.filters());

        log.info("[RetrievalNode] Retrieved {} documents", docs.size());

        if (docs.isEmpty()) {
            return Map.of(
                    "route",         "FALLBACK",
                    "fallbackReason", "No relevant documents found in the knowledge base"
            );
        }

        return Map.of("documents", docs);
    }
}
