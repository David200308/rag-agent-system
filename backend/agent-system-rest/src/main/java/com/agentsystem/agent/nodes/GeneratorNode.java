package com.agentsystem.agent.nodes;

import com.agentsystem.agent.service.GenerationService;
import com.agentsystem.agent.ToolCallBudget;
import com.agentsystem.agent.state.AgentState;
import com.agentsystem.config.ChatModelFactory;
import com.agentsystem.config.LlmProperties;
import com.agentsystem.connector.tool.GoogleCalendarAgentTool;
import com.agentsystem.connector.tool.GoogleDocsAgentTool;
import com.agentsystem.connector.tool.GoogleSheetsAgentTool;
import com.agentsystem.connector.tool.GoogleSlidesAgentTool;
import com.agentsystem.connector.tool.TelegramAgentTool;
import com.agentsystem.connector.tool.TravelAgentTool;
import com.agentsystem.model.entity.ModelConfig;
import com.agentsystem.model.service.ModelConfigService;
import com.agentsystem.schema.AgentRequest;
import com.agentsystem.schema.AgentResponse;
import com.agentsystem.schema.DocumentResult;
import com.agentsystem.schema.QueryAnalysis;
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

    private final ChatClient            chatClient;
    private final LlmProperties         llmProperties;
    private final ModelConfigService    modelConfigService;
    private final ChatModelFactory      chatModelFactory;
    private final GenerationService     generationService;
    private final ToolCallBudget        toolCallBudget;
    private final GoogleDocsAgentTool      googleDocsTool;
    private final GoogleSheetsAgentTool    googleSheetsTool;
    private final GoogleSlidesAgentTool    googleSlidesTool;
    private final GoogleCalendarAgentTool  googleCalendarTool;
    private final TelegramAgentTool        telegramTool;
    private final TravelAgentTool          travelTool;

    private static final String SYSTEM_PROMPT = """
            You are a helpful, accurate AI assistant with access to Google Workspace, Google Calendar, and Telegram \
            tools.

            GOOGLE WORKSPACE TOOLS — you have these tools available and MUST call them when relevant:
            - readGoogleDoc:       call when the user provides a docs.google.com/document URL
            - readGoogleSheet:     call when the user provides a docs.google.com/spreadsheets URL
            - readGoogleSlide:     call when the user provides a docs.google.com/presentation URL
            - writeToGoogleDocs:   call when the user asks to write, save, or export text or \
            conversation content to Google Docs
            - writeToGoogleSheets: call when the user asks to save tabular data or tables to Google Sheets
            - writeToGoogleSlides: call when the user asks to create a presentation in Google Slides

            GOOGLE CALENDAR TOOLS:
            - listUpcomingEvents:  call when the user asks about their schedule, upcoming meetings, \
            "what's on my calendar", "what do I have today/this week", or similar. Specify maxResults (default 10).
            - createCalendarEvent: call when the user says "schedule a meeting", "add to my calendar", \
            "create an event", or similar. Requires title, startDateTime, endDateTime (ISO 8601 / UTC). \
            description and location are optional.

            TELEGRAM TOOLS:
            - sendTelegramMessage: call when the user says "send this to my Telegram", \
            "message me on Telegram", "notify me via Telegram", or similar phrases. \
            Pass the message content the user wants to receive.
            - createTelegramGroupSession: call ONLY in a shared conversation when the user asks \
            to "send to Telegram", "create a Telegram group", or "notify both of us on Telegram". \
            This sends the content to both the conversation owner and the current user's Telegram.

            Investment price alerts (crypto/stock) are managed only from the Financial section of the \
            app UI, not via chat — if the user asks to set up a price alert, tell them to use the \
            bell/alert icon next to the symbol in the Financial section.

            TRAVEL TOOL:
            - getTravelRecords: call this for ANY question about the user's trips, travel \
            history, itinerary, flights, or travel expenses/notes/route. You have no way of \
            knowing which trips (if any) are shared with chat without calling it — never guess \
            or answer from assumption. Base your answer strictly on what the tool actually \
            returns: if it returns trip data, answer from that data; if it says no trips are \
            visible, tell the user (in your own words) that none of their trips are currently \
            shared with chat and that they can enable it via that trip's "Allow Chat?" toggle.

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
            5. For ANY question about the user's trips, travel history, itinerary, flights, or \
            travel expenses, you MUST call getTravelRecords before answering — this prompt's \
            description of that tool is not itself an answer, it only tells you when to call it.

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
        String userUuid         = state.userUuid().orElse(null);
        String orgId            = state.orgId().orElse(null);
        String shareOwnerEmail  = state.shareOwnerEmail().orElse(null);

        ModelConfig selectedConfig = state.selectedModelDisplayName()
                .flatMap(modelConfigService::findByDisplayName)
                .filter(ModelConfig::isEnabled)
                .orElse(null);
        ChatClient effectiveClient = selectedConfig != null
                ? chatModelFactory.buildChatClient(selectedConfig)
                : chatClient;

        log.debug("[GeneratorNode] Generating answer (docs={} model={})", docs.size(),
                selectedConfig != null ? selectedConfig.getDisplayName() : "default");

        // Inject per-request user_uuid and orgId so tools know which token to use
        googleDocsTool.setCurrentUserUuid(userUuid);
        googleDocsTool.setCurrentOrgId(orgId);
        googleSheetsTool.setCurrentUserUuid(userUuid);
        googleSheetsTool.setCurrentOrgId(orgId);
        googleSlidesTool.setCurrentUserUuid(userUuid);
        googleSlidesTool.setCurrentOrgId(orgId);
        googleCalendarTool.setCurrentUserUuid(userUuid);
        googleCalendarTool.setCurrentOrgId(orgId);
        telegramTool.setCurrentUserUuid(userUuid);
        telegramTool.setCurrentOrgId(orgId);
        telegramTool.setShareOwnerEmail(shareOwnerEmail);
        travelTool.setCurrentUserUuid(userUuid);
        toolCallBudget.reset();
        String answer;
        try {
            ToolCallbackProvider tools = MethodToolCallbackProvider.builder()
                    .toolObjects(googleDocsTool, googleSheetsTool, googleSlidesTool,
                                 googleCalendarTool, telegramTool, travelTool)
                    .build();

            answer = generationService.generate(effectiveClient, SYSTEM_PROMPT, userPrompt, tools).join();
        } finally {
            googleDocsTool.clearCurrentUserUuid();
            googleDocsTool.clearCurrentOrgId();
            googleSheetsTool.clearCurrentUserUuid();
            googleSheetsTool.clearCurrentOrgId();
            googleSlidesTool.clearCurrentUserUuid();
            googleSlidesTool.clearCurrentOrgId();
            googleCalendarTool.clearCurrentUserUuid();
            googleCalendarTool.clearCurrentOrgId();
            telegramTool.clearCurrentUserUuid();
            telegramTool.clearCurrentOrgId();
            telegramTool.clearShareOwnerEmail();
            travelTool.clearCurrentUserUuid();
            toolCallBudget.clear();
        }

        if (answer == null) {
            log.warn("[GeneratorNode] LLM generation failed after retries — routing to fallback");
            return Map.of(
                    "route",         "FALLBACK",
                    "fallbackReason", "LLM generation failed after retries"
            );
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
                        selectedConfig != null ? selectedConfig.getModelId() : resolveModelName(),
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
            case "deepseek"   -> llmProperties.getDeepseek().getModel();
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
