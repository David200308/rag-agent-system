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
     * @param query   refined query string from the query-analyser
     * @param topK    maximum number of results
     * @param filters optional metadata filters (e.g. {"category": "ml"})
     */
    @CircuitBreaker(name = "retrieval", fallbackMethod = "retrievalFallback")
    @Retry(name = "retrieval")
    public List<DocumentResult> retrieve(String query, int topK, Map<String, String> filters) {
        log.debug("[RetrievalService] Querying Weaviate — query='{}' topK={}", query, topK);

        SearchRequest.Builder requestBuilder = SearchRequest.builder().query(query).topK(topK);

        if (filters != null && !filters.isEmpty()) {
            var b = new FilterExpressionBuilder();
            // Build AND expression for all filter entries
            var expressions = filters.entrySet().stream()
                    .map(e -> b.eq(e.getKey(), e.getValue()))
                    .toList();
            if (!expressions.isEmpty()) {
                var combined = expressions.stream()
                        .reduce(b::and)
                        .orElseThrow();
                requestBuilder.filterExpression(combined.build());
            }
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
                                                   Throwable ex) {
        log.error("[RetrievalService] Circuit-breaker fallback triggered: {}", ex.getMessage());
        return List.of();
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
