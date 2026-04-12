package com.ragagent.agent.nodes;

import com.ragagent.agent.state.AgentState;
import com.ragagent.schema.AgentRequest;
import com.ragagent.schema.QueryAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Node 1 — Query Analyser.
 *
 * Uses Spring AI's {@link BeanOutputConverter} (Java's Pydantic equivalent) to
 * instruct the LLM to return a well-typed {@link QueryAnalysis} JSON object.
 * The converter generates a JSON-Schema from the record's annotations, appends
 * it to the prompt, then validates the response against Bean Validation rules.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryAnalyzerNode {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
            You are a query-analysis expert for a Retrieval-Augmented Generation (RAG) system.
            Given a user query, you must:
            1. Rephrase it to be more specific and retrieval-friendly (refinedQuery).
            2. Decide the routing:
               - RETRIEVE  → use for ANY query about specific facts, personal data, events, spending,
                             travel, documents, or anything that may have been stored in the knowledge base.
                             When in doubt, always prefer RETRIEVE over FALLBACK.
               - DIRECT    → ONLY for clearly general knowledge (e.g. "what is the capital of France?"),
                             greetings, or simple conversational exchanges that require no personal context.
               - FALLBACK  → ONLY for queries that are genuinely harmful, abusive, or completely
                             nonsensical (e.g. random gibberish). Never use FALLBACK just because
                             the answer might involve personal or specific data — use RETRIEVE instead.
            3. Extract key entities / keywords.
            4. Optionally decompose complex queries into sub-questions.
            5. Provide a brief reasoning for your routing decision.
            6. Estimate your confidence in the routing decision (0.0–1.0).

            Respond ONLY with valid JSON matching the provided schema.
            """;

    /**
     * Called by the LangGraph compiled graph.
     *
     * @return partial-state map merged into {@link AgentState}
     */
    public Map<String, Object> process(AgentState state) {
        AgentRequest request = state.request()
                .orElseThrow(() -> new IllegalStateException("No request in state"));

        log.debug("[QueryAnalyzerNode] Analysing query: {}", request.query());

        // BeanOutputConverter generates JSON-Schema from QueryAnalysis and
        // appends the format instructions to the prompt automatically.
        var converter = new BeanOutputConverter<>(QueryAnalysis.class);

        String rawResponse = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(u -> u.text("""
                        User query: {query}

                        {format}
                        """)
                        .param("query", request.query())
                        .param("format", converter.getFormat()))
                .call()
                .content();

        QueryAnalysis analysis = converter.convert(rawResponse);
        log.info("[QueryAnalyzerNode] Route={} confidence={} refinedQuery={}",
                analysis.route(), analysis.routeConfidence(), analysis.refinedQuery());

        return Map.of(
                "queryAnalysis", analysis,
                "route", analysis.route().name()
        );
    }
}
