package com.agentsystem.agent.nodes;

import com.agentsystem.agent.state.AgentState;
import com.agentsystem.config.ChatModelFactory;
import com.agentsystem.model.entity.ModelConfig;
import com.agentsystem.model.service.ModelConfigService;
import com.agentsystem.schema.AgentRequest;
import com.agentsystem.schema.QueryAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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

    private final ChatClient         chatClient;
    private final ModelConfigService modelConfigService;
    private final ChatModelFactory   chatModelFactory;

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

    /** Appended to {@link #SYSTEM_PROMPT} only when the caller attached a persistent sandbox for this request. */
    private static final String SANDBOX_ADDENDUM = """

            The user has attached a shell sandbox to this conversation. Route requests to run,
            execute, install, check, or inspect something in that sandbox (by any name the user
            gives it) as DIRECT — they need the sandbox tool, not the knowledge base.
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

        ModelConfig selectedConfig = state.selectedModelDisplayName()
                .flatMap(modelConfigService::findByDisplayName)
                .filter(ModelConfig::isEnabled)
                .orElse(null);
        ChatClient effectiveClient = selectedConfig != null
                ? chatModelFactory.buildChatClient(selectedConfig)
                : chatClient;

        // BeanOutputConverter generates JSON-Schema from QueryAnalysis and
        // appends the format instructions to the prompt automatically.
        var converter = new BeanOutputConverter<>(QueryAnalysis.class);

        // Tell the analyser a sandbox is attached so "run/install/check X" requests go DIRECT to the
        // tool-enabled generator instead of a pointless KB lookup (or FALLBACK, which has no tools).
        String systemPrompt = state.hasSandbox() ? SYSTEM_PROMPT + SANDBOX_ADDENDUM : SYSTEM_PROMPT;

        String rawResponse = effectiveClient.prompt()
                .system(systemPrompt)
                .user(u -> u.text("""
                        User query: {query}

                        {format}
                        """)
                        .param("query", request.query())
                        .param("format", converter.getFormat()))
                .call()
                .content();

        QueryAnalysis analysis = parseAnalysis(converter, rawResponse);
        log.info("[QueryAnalyzerNode] Route={} confidence={} refinedQuery={}",
                analysis.route(), analysis.routeConfidence(), analysis.refinedQuery());

        // If the caller disabled KB search for this request, force DIRECT so
        // RetrievalNode is bypassed entirely, regardless of what the LLM decided.
        String route = analysis.route().name();
        if (!request.isKnowledgeBaseEnabled() && QueryAnalysis.Route.RETRIEVE.name().equals(route)) {
            log.info("[QueryAnalyzerNode] Knowledge base disabled — routing DIRECT");
            route = QueryAnalysis.Route.DIRECT.name();
        }

        return Map.of(
                "queryAnalysis", analysis,
                "route", route
        );
    }

    /**
     * Some reasoning models (e.g. Qwen's "thinking" checkpoints on OpenRouter) pad their response
     * with plain-prose reasoning that Spring AI's {@link BeanOutputConverter} cleaner can't strip —
     * it only recognises {@code <think>} tags or fenced code blocks. Worse, that prose can itself
     * discuss the JSON schema (mentioning "$schema", "required", or literal "{ ... }" placeholders),
     * so naively slicing between the first "{" and the last "}" can grab a bogus fragment instead of
     * the real answer. Extract every balanced, string-aware top-level JSON object in the text and
     * try each — starting with the last, since the real answer is typically emitted after any
     * reasoning preamble — until one actually parses.
     */
    private static QueryAnalysis parseAnalysis(BeanOutputConverter<QueryAnalysis> converter, String rawResponse) {
        List<String> candidates = extractJsonObjects(rawResponse);
        for (int i = candidates.size() - 1; i >= 0; i--) {
            try {
                return converter.convert(candidates.get(i));
            } catch (RuntimeException ignored) {
                // try the next candidate — see method javadoc for why more than one may exist
            }
        }
        return converter.convert(rawResponse);
        // Falls through to the original text (and its original exception) when no candidate parses
        // or none were found, so the failure message still reflects what the model actually sent.
    }

    /** Finds every balanced {@code {...}} substring at brace-depth 0, ignoring braces inside string literals. */
    private static List<String> extractJsonObjects(String text) {
        List<String> results = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}' && depth > 0) {
                depth--;
                if (depth == 0 && start >= 0) {
                    results.add(text.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return results;
    }
}
