package com.agentsystem.agent.nodes;

import com.agentsystem.agent.GenerationService;
import com.agentsystem.agent.ToolCallBudget;
import com.agentsystem.agent.state.AgentState;
import com.agentsystem.config.ChatModelFactory;
import com.agentsystem.config.LlmProperties;
import com.agentsystem.connector.GoogleCalendarAgentTool;
import com.agentsystem.connector.GoogleDocsAgentTool;
import com.agentsystem.connector.GoogleSheetsAgentTool;
import com.agentsystem.connector.GoogleSlidesAgentTool;
import com.agentsystem.connector.TelegramAgentTool;
import com.agentsystem.model.ModelConfigService;
import com.agentsystem.schema.AgentRequest;
import com.agentsystem.schema.AgentResponse;
import com.agentsystem.schema.DocumentResult;
import com.agentsystem.schema.QueryAnalysis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeneratorNodeTest {

    @Mock ChatClient            chatClient;
    @Mock LlmProperties         llmProperties;
    @Mock ModelConfigService    modelConfigService;
    @Mock ChatModelFactory      chatModelFactory;
    @Mock GenerationService     generationService;
    @Mock ToolCallBudget        toolCallBudget;
    @Mock GoogleDocsAgentTool      googleDocsTool;
    @Mock GoogleSheetsAgentTool    googleSheetsTool;
    @Mock GoogleSlidesAgentTool    googleSlidesTool;
    @Mock GoogleCalendarAgentTool  googleCalendarTool;
    @Mock TelegramAgentTool        telegramTool;

    GeneratorNode node;

    @BeforeEach
    void setUp() {
        node = new GeneratorNode(chatClient, llmProperties, modelConfigService, chatModelFactory,
                generationService, toolCallBudget,
                googleDocsTool, googleSheetsTool, googleSlidesTool, googleCalendarTool, telegramTool);
    }

    private static AgentRequest request() {
        return new AgentRequest("question?", null, 5, null, false, null, null, true, null);
    }

    private static QueryAnalysis analysis() {
        return new QueryAnalysis("refined question", QueryAnalysis.Route.DIRECT,
                0.9, List.of(), null, "direct answer");
    }

    // ── process — success ──────────────────────────────────────────────────────

    @Test
    void process_generationSucceeds_returnsResponse() {
        when(generationService.generate(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("The answer."));
        when(llmProperties.getProvider()).thenReturn("openai");
        LlmProperties.OpenAiProps openAiProps = new LlmProperties.OpenAiProps();
        openAiProps.setModel("gpt-4o-mini");
        when(llmProperties.getOpenai()).thenReturn(openAiProps);

        AgentState state = new AgentState(Map.of(
                "request",       request(),
                "queryAnalysis", analysis()
        ));

        Map<String, Object> result = node.process(state);

        AgentResponse response = (AgentResponse) result.get("response");
        assertThat(response).isNotNull();
        assertThat(response.answer()).isEqualTo("The answer.");
        verify(toolCallBudget).reset();
        verify(toolCallBudget).clear();
    }

    // ── process — LLM circuit-breaker/retry exhaustion ────────────────────────

    @Test
    void process_generationFailsAfterRetries_routesToFallback() {
        when(generationService.generate(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        AgentState state = new AgentState(Map.of(
                "request",       request(),
                "queryAnalysis", analysis()
        ));

        Map<String, Object> result = node.process(state);

        assertThat(result).containsEntry("route", "FALLBACK");
        assertThat(result).containsKey("fallbackReason");
        verify(toolCallBudget).clear();
    }

    // ── buildPrompt — no documents ────────────────────────────────────────────

    @Test
    void buildPrompt_noDocs_returnsCurrentQuestion() throws Exception {
        QueryAnalysis analysis = new QueryAnalysis(
                "refined query", QueryAnalysis.Route.DIRECT, 0.9, List.of(), List.of(), "reasoning");

        String prompt = callBuildPrompt("What is Java?", analysis, List.of(), null);

        assertThat(prompt).contains("Current Question");
        assertThat(prompt).contains("What is Java?");
        assertThat(prompt).doesNotContain("Context Documents");
    }

    @Test
    void buildPrompt_withDocs_includesContextSection() throws Exception {
        QueryAnalysis analysis = new QueryAnalysis(
                "refined query", QueryAnalysis.Route.RETRIEVE, 0.9, List.of(), List.of(), "reasoning");
        DocumentResult doc = new DocumentResult("id-1", "Java is a language.", 0.95, "java.pdf", null);

        String prompt = callBuildPrompt("What is Java?", analysis, List.of(doc), null);

        assertThat(prompt).contains("Context Documents");
        assertThat(prompt).contains("java.pdf");
        assertThat(prompt).contains("Java is a language.");
        assertThat(prompt).contains("What is Java?");
        // score is formatted in the prompt
    }

    @Test
    void buildPrompt_withHistory_includesConversationHistory() throws Exception {
        QueryAnalysis analysis = new QueryAnalysis(
                "refined", QueryAnalysis.Route.DIRECT, 0.9, List.of(), List.of(), "reasoning");
        AgentRequest.ConversationTurn userTurn = new AgentRequest.ConversationTurn("user", "Hi there");
        AgentRequest.ConversationTurn asstTurn = new AgentRequest.ConversationTurn("assistant", "Hello!");

        String prompt = callBuildPrompt("How are you?", analysis, List.of(), List.of(userTurn, asstTurn));

        assertThat(prompt).contains("Conversation History");
        assertThat(prompt).contains("User");
        assertThat(prompt).contains("Hi there");
        assertThat(prompt).contains("Assistant");
        assertThat(prompt).contains("Hello!");
    }

    @Test
    void buildPrompt_withSubQuestions_includesSubQuestionsSection() throws Exception {
        QueryAnalysis analysis = new QueryAnalysis(
                "refined query", QueryAnalysis.Route.RETRIEVE, 0.9,
                List.of("Java"),
                List.of("What is inheritance?", "What is polymorphism?"),
                "reasoning");
        DocumentResult doc = new DocumentResult("id-1", "Java OOP concepts.", 0.95, "java.pdf", null);

        String prompt = callBuildPrompt("Explain OOP", analysis, List.of(doc), null);

        assertThat(prompt).contains("sub-questions");
        assertThat(prompt).contains("What is inheritance?");
        assertThat(prompt).contains("What is polymorphism?");
    }

    @Test
    void buildPrompt_noSubQuestions_omitsSubQuestionsSection() throws Exception {
        QueryAnalysis analysis = new QueryAnalysis(
                "refined", QueryAnalysis.Route.RETRIEVE, 0.9, List.of(), List.of(), "reasoning");
        DocumentResult doc = new DocumentResult("id-1", "Content.", 0.9, "doc.pdf", null);

        String prompt = callBuildPrompt("Question", analysis, List.of(doc), null);

        assertThat(prompt).doesNotContain("sub-questions");
    }

    @Test
    void buildPrompt_multipleDocuments_includesAllSources() throws Exception {
        QueryAnalysis analysis = new QueryAnalysis(
                "refined", QueryAnalysis.Route.RETRIEVE, 0.9, List.of(), List.of(), "reasoning");
        DocumentResult doc1 = new DocumentResult("id-1", "Content A.", 0.9, "file-a.pdf", null);
        DocumentResult doc2 = new DocumentResult("id-2", "Content B.", 0.8, "file-b.pdf", null);

        String prompt = callBuildPrompt("Question", analysis, List.of(doc1, doc2), null);

        assertThat(prompt).contains("file-a.pdf");
        assertThat(prompt).contains("file-b.pdf");
        assertThat(prompt).contains("Content A.");
        assertThat(prompt).contains("Content B.");
    }

    // ── toSourceDocs ──────────────────────────────────────────────────────────

    @Test
    void toSourceDocs_mapsAllFields() throws Exception {
        DocumentResult doc = new DocumentResult("id-1", "Content here.", 0.95, "source.pdf", null);

        var sourceDocs = callToSourceDocs(List.of(doc));

        assertThat(sourceDocs).hasSize(1);
        assertThat(sourceDocs.get(0).id()).isEqualTo("id-1");
        assertThat(sourceDocs.get(0).content()).isEqualTo("Content here.");
        assertThat(sourceDocs.get(0).source()).isEqualTo("source.pdf");
        assertThat(sourceDocs.get(0).score()).isEqualTo(0.95);
    }

    @Test
    void toSourceDocs_emptyList_returnsEmptyList() throws Exception {
        var sourceDocs = callToSourceDocs(List.of());
        assertThat(sourceDocs).isEmpty();
    }

    @Test
    void toSourceDocs_multipleDocs_preservesOrder() throws Exception {
        DocumentResult d1 = new DocumentResult("id-1", "A", 0.9, "a.pdf", null);
        DocumentResult d2 = new DocumentResult("id-2", "B", 0.8, "b.pdf", null);
        DocumentResult d3 = new DocumentResult("id-3", "C", 0.7, "c.pdf", null);

        var result = callToSourceDocs(List.of(d1, d2, d3));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).id()).isEqualTo("id-1");
        assertThat(result.get(1).id()).isEqualTo("id-2");
        assertThat(result.get(2).id()).isEqualTo("id-3");
    }

    // ── resolveModelName ──────────────────────────────────────────────────────

    @Test
    void resolveModelName_anthropic_returnsAnthropicModel() throws Exception {
        when(llmProperties.getProvider()).thenReturn("anthropic");
        LlmProperties.AnthropicProps aProps = new LlmProperties.AnthropicProps();
        aProps.setModel("claude-3-haiku");
        when(llmProperties.getAnthropic()).thenReturn(aProps);

        String model = callResolveModelName();

        assertThat(model).isEqualTo("claude-3-haiku");
    }

    @Test
    void resolveModelName_openrouter_returnsOpenRouterModel() throws Exception {
        when(llmProperties.getProvider()).thenReturn("openrouter");
        LlmProperties.OpenRouterProps orProps = new LlmProperties.OpenRouterProps();
        orProps.setModel("anthropic/claude-3-haiku");
        when(llmProperties.getOpenrouter()).thenReturn(orProps);

        String model = callResolveModelName();

        assertThat(model).isEqualTo("anthropic/claude-3-haiku");
    }

    @Test
    void resolveModelName_local_returnsLocalModel() throws Exception {
        when(llmProperties.getProvider()).thenReturn("local");
        LlmProperties.LocalProps localProps = new LlmProperties.LocalProps();
        localProps.setModel("llama3");
        when(llmProperties.getLocal()).thenReturn(localProps);

        String model = callResolveModelName();

        assertThat(model).isEqualTo("llama3");
    }

    @Test
    void resolveModelName_deepseek_returnsDeepSeekModel() throws Exception {
        when(llmProperties.getProvider()).thenReturn("deepseek");
        LlmProperties.DeepSeekProps dsProps = new LlmProperties.DeepSeekProps();
        dsProps.setModel("deepseek-chat");
        when(llmProperties.getDeepseek()).thenReturn(dsProps);

        String model = callResolveModelName();

        assertThat(model).isEqualTo("deepseek-chat");
    }

    @Test
    void resolveModelName_defaultFallsBackToOpenai() throws Exception {
        when(llmProperties.getProvider()).thenReturn("openai");
        LlmProperties.OpenAiProps oaiProps = new LlmProperties.OpenAiProps();
        oaiProps.setModel("gpt-4o-mini");
        when(llmProperties.getOpenai()).thenReturn(oaiProps);

        String model = callResolveModelName();

        assertThat(model).isEqualTo("gpt-4o-mini");
    }

    @Test
    void resolveModelName_unknownProvider_fallsBackToOpenai() throws Exception {
        when(llmProperties.getProvider()).thenReturn("unknown-provider");
        LlmProperties.OpenAiProps oaiProps = new LlmProperties.OpenAiProps();
        oaiProps.setModel("gpt-4o");
        when(llmProperties.getOpenai()).thenReturn(oaiProps);

        String model = callResolveModelName();

        assertThat(model).isEqualTo("gpt-4o");
    }

    // ── reflection helpers ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String callBuildPrompt(String query, QueryAnalysis analysis,
                                   List<DocumentResult> docs,
                                   List<AgentRequest.ConversationTurn> history) throws Exception {
        Method m = GeneratorNode.class.getDeclaredMethod(
                "buildPrompt", String.class, QueryAnalysis.class, List.class, List.class);
        m.setAccessible(true);
        return (String) m.invoke(node, query, analysis, docs, history);
    }

    @SuppressWarnings("unchecked")
    private java.util.List<com.agentsystem.schema.AgentResponse.SourceDocument> callToSourceDocs(
            List<DocumentResult> docs) throws Exception {
        Method m = GeneratorNode.class.getDeclaredMethod("toSourceDocs", List.class);
        m.setAccessible(true);
        return (java.util.List<com.agentsystem.schema.AgentResponse.SourceDocument>) m.invoke(node, docs);
    }

    private String callResolveModelName() throws Exception {
        Method m = GeneratorNode.class.getDeclaredMethod("resolveModelName");
        m.setAccessible(true);
        return (String) m.invoke(node);
    }
}
