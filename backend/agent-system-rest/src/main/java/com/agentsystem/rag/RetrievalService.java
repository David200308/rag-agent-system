package com.ragagent.rag;

import com.ragagent.schema.DocumentResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service that queries Weaviate via Spring AI's {@link VectorStore} abstraction.
 *
 * Resilience4j annotations provide:
 *   @CircuitBreaker — open circuit if Weaviate becomes unavailable
 *   @Retry          — retry transient IO failures up to 3 times
 *   @TimeLimiter    — fail fast after 5 s to avoid blocking virtual threads
 *
 * All fallback methods redirect to {@code FallbackService} through the graph.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final VectorStore vectorStore;

    /**
     * Retrieve the top-K most similar document chunks for {@code query}.
     *
     * @param query          refined query string from the query-analyser
     * @param topK           maximum number of results
     * @param filters        optional user-supplied metadata filters (e.g. {"category": "ml"})
     * @param allowedSources when non-null, restricts results to chunks whose {@code source}
     *                       metadata is in this set (security boundary). An empty set means
     *                       the caller has no accessible sources — returns nothing.
     */
    @CircuitBreaker(name = "retrieval", fallbackMethod = "retrievalFallback")
    @Retry(name = "retrieval")
    public List<DocumentResult> retrieve(String query, int topK,
                                         Map<String, String> filters,
                                         Set<String> allowedSources) {
        log.debug("[RetrievalService] Querying Weaviate — query='{}' topK={}", query, topK);

        // Security guard: if the caller has no accessible sources, return nothing immediately.
        if (allowedSources != null && allowedSources.isEmpty()) {
            log.info("[RetrievalService] No accessible sources for caller — returning empty result");
            return List.of();
        }

        SearchRequest.Builder requestBuilder = SearchRequest.builder().query(query).topK(topK);

        var b = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op securityFilter = buildSourceFilter(b, allowedSources);
        FilterExpressionBuilder.Op userFilter     = buildUserFilters(b, filters);

        FilterExpressionBuilder.Op combined = and(b, securityFilter, userFilter);
        if (combined != null) {
            requestBuilder.filterExpression(combined.build());
        }

        List<Document> docs = vectorStore.similaritySearch(requestBuilder.build());

        return docs.stream()
                .map(this::toDocumentResult)
                .filter(d -> d.score() > 0)
                .toList();
    }

    /** Resilience4j fallback — returns empty list so the graph routes to FallbackNode. */
    public List<DocumentResult> retrievalFallback(String query, int topK,
                                                   Map<String, String> filters,
                                                   Set<String> allowedSources,
                                                   Throwable ex) {
        log.error("[RetrievalService] Circuit-breaker fallback triggered: {}", ex.getMessage());
        return List.of();
    }

    /**
     * Build an OR chain filtering by source (security boundary).
     * Returns null when {@code allowedSources} is null (auth disabled — no restriction).
     */
    private FilterExpressionBuilder.Op buildSourceFilter(FilterExpressionBuilder b,
                                                          Set<String> allowedSources) {
        if (allowedSources == null) return null;
        return allowedSources.stream()
                .map(src -> (FilterExpressionBuilder.Op) b.eq("source", src))
                .reduce(b::or)
                .orElse(null);
    }

    /** Build an AND chain from user-supplied filters. Returns null when filters is empty. */
    private FilterExpressionBuilder.Op buildUserFilters(FilterExpressionBuilder b,
                                                         Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) return null;
        return filters.entrySet().stream()
                .map(e -> (FilterExpressionBuilder.Op) b.eq(e.getKey(), e.getValue()))
                .reduce(b::and)
                .orElse(null);
    }

    /** AND two nullable filter ops together. */
    private FilterExpressionBuilder.Op and(FilterExpressionBuilder b,
                                            FilterExpressionBuilder.Op left,
                                            FilterExpressionBuilder.Op right) {
        if (left  == null) return right;
        if (right == null) return left;
        return b.and(left, right);
    }

    private DocumentResult toDocumentResult(Document doc) {
        Object score = doc.getMetadata().getOrDefault("distance", 0.0);
        double similarity = score instanceof Number n ? 1.0 - n.doubleValue() : 0.0;

        return new DocumentResult(
                doc.getId(),
                doc.getText(),
                similarity,
                (String) doc.getMetadata().getOrDefault("source", "unknown"),
                doc.getMetadata()
        );
    }
}
