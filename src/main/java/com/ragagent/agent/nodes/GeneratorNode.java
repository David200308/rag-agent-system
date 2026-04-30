package com.ragagent.agent.nodes;

import com.ragagent.agent.state.AgentState;
import com.ragagent.config.LlmProperties;
import com.ragagent.connector.GoogleDocsAgentTool;
import com.ragagent.connector.GoogleSheetsAgentTool;
import com.ragagent.connector.GoogleSlidesAgentTool;
import com.ragagent.schema.AgentRequest;
import com.ragagent.schema.AgentResponse;
import com.ragagent.schema.DocumentResult;
import com.ragagent.schema.QueryAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
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

    private final ChatClient           chatClient;
    private final LlmProperties        llmProperties;
    private final GoogleDocsAgentTool   googleDocsTool;
    private final GoogleSheetsAgentTool googleSheetsTool;
    private final GoogleSlidesAgentTool googleSlidesTool;

    private static final String SYSTEM_PROMPT = """
            You are a helpful, accurate AI assistant with access to Google Workspace tools.

            GOOGLE WORKSPACE TOOLS — you have these tools available and MUST call them when relevant:
            - readGoogleDoc:       call when the user provides a docs.google.com/document URL
            - readGoogleSheet:     call when the user provides a docs.google.com/spreadsheets URL
            - readGoogleSlide:     call when the user provides a docs.google.com/presentation URL
            - writeToGoogleDocs:   call when the user asks to write, save, or export text or \
            conversation content to Google Docs
            - writeToGoogleSheets: call when the user asks to save tabular data or tables to Google Sheets
            - writeToGoogleSlides: call when the user asks to create a presentation in Google Slides

            CRITICAL RULES for Google Workspace requests:
            1. When the user provides any docs.google.com URL, you MUST call the matching read tool \
            (readGoogleDoc / readGoogleSheet / readGoogleSlide) — never fetch these URLs as web pages.
            2. When the user asks to write to Google Docs, Sheets, or Slides, you MUST call the \
            appropriate tool directly.
            3. Do NOT give manual instructions like "go to Google Docs and paste". \
            Do NOT explain how to do it manually. Just call the tool with the content.
            4. When the user refers to "this conversation", "the content generated before", \
            "what was said above", or similar phrases, extract the full relevant text from the \
            Conversation History section of the prompt and pass it as the content to the tool.

            When source documents are provided, ground your answer strictly in those documents
            and cite them. If documents are irrelevant, say so rather than hallucinating.
            Be concise but complete.

            FORMATTING (when not calling a tool):
            - Always respond in plain Markdown (paragraphs, bullet lists, numbered lists, tables).
            - Use Markdown tables when comparing or summarising structured data.
            - NEVER wrap your answer in JSON, code fences, or any structured data format
              unless the user explicitly asks for JSON or code output.
            """;

    public Map<String, Object> process(AgentState state) {
        long start = System.currentTimeMillis();

        AgentRequest  request  = state.request().orElseThrow();
        QueryAnalysis analysis = state.queryAnalysis().orElseThrow();
        List<DocumentResult> docs = state.documents();

        String userPrompt = buildPrompt(request.query(), analysis, docs, request.conversationHistory());
        String userEmail  = state.userEmail().orElse(null);

        log.debug("[GeneratorNode] Generating answer (docs={})", docs.size());

        // Inject per-request email so Google tools know which token to use
        googleDocsTool.setCurrentEmail(userEmail);
        googleSheetsTool.setCurrentEmail(userEmail);
        googleSlidesTool.setCurrentEmail(userEmail);
        String answer;
        try {
            ToolCallbackProvider tools = MethodToolCallbackProvider.builder()
                    .toolObjects(googleDocsTool, googleSheetsTool, googleSlidesTool)
                    .build();

            answer = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .toolCallbacks(tools)
                    .call()
                    .content();
        } finally {
            googleDocsTool.clearCurrentEmail();
            googleSheetsTool.clearCurrentEmail();
            googleSlidesTool.clearCurrentEmail();
        }

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
                               List<DocumentResult> docs,
                               List<AgentRequest.ConversationTurn> history) {
        StringBuilder sb = new StringBuilder();

        if (history != null && !history.isEmpty()) {
            sb.append("## Conversation History\n");
            for (AgentRequest.ConversationTurn turn : history) {
                sb.append("**").append("user".equals(turn.role()) ? "User" : "Assistant").append(":** ");
                sb.append(turn.content()).append("\n\n");
            }
            sb.append("\n");
        }

        if (docs.isEmpty()) {
            sb.append("## Current Question\n").append(query);
            return sb.toString();
        }

        String context = docs.stream()
                .map(d -> "### Source: %s (score=%.2f)\n%s".formatted(
                        d.source(), d.score(), d.content()))
                .collect(Collectors.joining("\n\n"));

        String subQSection = analysis.subQuestions() != null && !analysis.subQuestions().isEmpty()
                ? "\n\nConsider these sub-questions:\n" +
                  analysis.subQuestions().stream()
                          .map(q -> "- " + q)
                          .collect(Collectors.joining("\n"))
                : "";

        sb.append("""
               ## Context Documents
               %s

               ## Current Question
               %s%s

               Answer using the context above. Cite sources inline as [Source: <name>].
               """.formatted(context, query, subQSection));

        return sb.toString();
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
