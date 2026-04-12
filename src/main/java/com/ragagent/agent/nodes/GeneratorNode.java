package com.ragagent.agent.nodes;

import com.ragagent.agent.state.AgentState;
import com.ragagent.config.LlmProperties;
import com.ragagent.schema.AgentRequest;
import com.ragagent.schema.AgentResponse;
import com.ragagent.schema.DocumentResult;
import com.ragagent.schema.QueryAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Node 3 — Generator.
 *
 * Synthesises a grounded answer from the retrieved documents (RAG) or answers
 * directly from LLM knowledge when routing is DIRECT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeneratorNode {

    private final ChatClient chatClient;
    private final LlmProperties llmProperties;

    private static final String SYSTEM_PROMPT = """
            You are a helpful, accurate AI assistant. When source documents are
            provided, ground your answer strictly in those documents and cite them.
            If documents are irrelevant, say so rather than hallucinating.
            Be concise but complete.
            """;

    public Map<String, Object> process(AgentState state) {
        long start = System.currentTimeMillis();

        AgentRequest  request  = state.request().orElseThrow();
        QueryAnalysis analysis = state.queryAnalysis().orElseThrow();
        List<DocumentResult> docs = state.documents();

        String userPrompt = buildPrompt(request.query(), analysis, docs);

        log.debug("[GeneratorNode] Generating answer (docs={})", docs.size());

        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        AgentResponse response = new AgentResponse(
                answer,
                toSourceDocs(docs),
                new AgentResponse.RouteDecision(
                        analysis.route().name(),
                        analysis.reasoning(),
                        analysis.routeConfidence()
                ),
                false,
                null,
                new AgentResponse.RunMetadata(
                        state.runId().orElse(UUID.randomUUID().toString()),
                        Instant.now(),
                        System.currentTimeMillis() - start,
                        docs.size(),
                        resolveModelName(),
                        null   // conversationId injected by AgentController after persistence
                )
        );

        log.info("[GeneratorNode] Answer generated ({} chars)", answer.length());
        return Map.of("response", response);
    }

    private String buildPrompt(String query,
                               QueryAnalysis analysis,
                               List<DocumentResult> docs) {
        if (docs.isEmpty()) {
            return "Answer the following from your own knowledge:\n\n" + query;
        }

        String context = docs.stream()
                .map(d -> "### Source: %s (score=%.2f)\n%s".formatted(
                        d.source(), d.score(), d.content()))
                .collect(Collectors.joining("\n\n"));

        // Multi-hop: guide the model through sub-questions if present
        String subQSection = analysis.subQuestions() != null && !analysis.subQuestions().isEmpty()
                ? "\n\nConsider these sub-questions:\n" +
                  analysis.subQuestions().stream()
                          .map(q -> "- " + q)
                          .collect(Collectors.joining("\n"))
                : "";

        return """
               ## Context Documents
               %s

               ## User Question
               %s%s

               Answer using the context above. Cite sources inline as [Source: <name>].
               """.formatted(context, query, subQSection);
    }

    private String resolveModelName() {
        return switch (llmProperties.getProvider().toLowerCase()) {
            case "anthropic"  -> llmProperties.getAnthropic().getModel();
            case "openrouter" -> llmProperties.getOpenrouter().getModel();
            case "local"      -> llmProperties.getLocal().getModel();
            default           -> llmProperties.getOpenai().getModel();
        };
    }

    private List<AgentResponse.SourceDocument> toSourceDocs(List<DocumentResult> docs) {
        return docs.stream()
                .map(d -> new AgentResponse.SourceDocument(
                        d.id(), d.content(), d.source(), d.score(), null))
                .toList();
    }
}
