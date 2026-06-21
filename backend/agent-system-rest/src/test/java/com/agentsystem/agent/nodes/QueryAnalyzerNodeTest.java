package com.agentsystem.agent.nodes;

import com.agentsystem.agent.state.AgentState;
import com.agentsystem.config.ChatModelFactory;
import com.agentsystem.model.ModelConfig;
import com.agentsystem.model.ModelConfigService;
import com.agentsystem.schema.AgentRequest;
import com.agentsystem.schema.QueryAnalysis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryAnalyzerNodeTest {

    @Mock ChatClient         chatClient;
    @Mock ModelConfigService modelConfigService;
    @Mock ChatModelFactory   chatModelFactory;

    // Nested mocks for ChatClient fluent API
    @Mock ChatClient.ChatClientRequestSpec  requestSpec;
    @Mock ChatClient.CallResponseSpec       callSpec;

    QueryAnalyzerNode node;

    @BeforeEach
    void setUp() {
        node = new QueryAnalyzerNode(chatClient, modelConfigService, chatModelFactory);
    }

    private AgentState stateWithQuery(String query) {
        AgentRequest request = new AgentRequest(
                query, null, 5, List.of(), false, null, List.of(), true, false);
        Map<String, Object> data = Map.of("request", request);
        return new AgentState(data);
    }

    private void stubChatClientToReturn(String json) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(json);
    }

    // ── process — default chatClient path ────────────────────────────────────

    @Test
    void process_noSelectedModel_usesDefaultChatClient() {
        AgentState state = stateWithQuery("What is Java?");
        String json = """
                {
                  "refinedQuery": "What is Java programming language?",
                  "route": "DIRECT",
                  "routeConfidence": 0.9,
                  "keywords": ["Java", "programming"],
                  "subQuestions": [],
                  "reasoning": "General knowledge query"
                }
                """;
        stubChatClientToReturn(json);
        // No selectedModelDisplayName in state → modelConfigService.findByDisplayName never called

        Map<String, Object> result = node.process(state);

        assertThat(result).containsKey("queryAnalysis");
        assertThat(result).containsKey("route");
        QueryAnalysis analysis = (QueryAnalysis) result.get("queryAnalysis");
        assertThat(analysis.route()).isEqualTo(QueryAnalysis.Route.DIRECT);
        assertThat(result.get("route")).isEqualTo("DIRECT");
    }

    @Test
    void process_retrieveRoute_setsRetrieveRoute() {
        AgentState state = stateWithQuery("What did I spend last month?");
        String json = """
                {
                  "refinedQuery": "personal spending last month",
                  "route": "RETRIEVE",
                  "routeConfidence": 0.95,
                  "keywords": ["spending", "last month"],
                  "subQuestions": [],
                  "reasoning": "Personal data query requires retrieval"
                }
                """;
        stubChatClientToReturn(json);

        Map<String, Object> result = node.process(state);

        assertThat(result.get("route")).isEqualTo("RETRIEVE");
    }

    @Test
    void process_withSelectedModel_usesModelFactoryClient() {
        Map<String, Object> data = Map.of(
                "request", new AgentRequest("query", null, 5, List.of(), false, null, List.of(), true, false),
                "selectedModelDisplayName", "GPT-4"
        );
        AgentState state = new AgentState(data);

        ModelConfig config = new ModelConfig();
        config.setDisplayName("GPT-4");
        config.setEnabled(true);
        config.setPlatform("openai");
        config.setModelId("gpt-4");

        when(modelConfigService.findByDisplayName("GPT-4")).thenReturn(Optional.of(config));

        // Set up the model factory to return a mock ChatClient
        ChatClient modelClient = mock(ChatClient.class);
        when(chatModelFactory.buildChatClient(config)).thenReturn(modelClient);

        ChatClient.ChatClientRequestSpec modelSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec modelCallSpec = mock(ChatClient.CallResponseSpec.class);
        when(modelClient.prompt()).thenReturn(modelSpec);
        when(modelSpec.system(anyString())).thenReturn(modelSpec);
        when(modelSpec.user(any(java.util.function.Consumer.class))).thenReturn(modelSpec);
        when(modelSpec.call()).thenReturn(modelCallSpec);
        when(modelCallSpec.content()).thenReturn("""
                {
                  "refinedQuery": "query",
                  "route": "DIRECT",
                  "routeConfidence": 0.9,
                  "keywords": [],
                  "subQuestions": [],
                  "reasoning": "general"
                }
                """);

        Map<String, Object> result = node.process(state);

        assertThat(result).containsKey("queryAnalysis");
        verify(chatModelFactory).buildChatClient(config);
        verify(modelClient).prompt();
    }

    @Test
    void process_disabledModel_fallsBackToDefaultClient() {
        Map<String, Object> data = Map.of(
                "request", new AgentRequest("query", null, 5, List.of(), false, null, List.of(), true, false),
                "selectedModelDisplayName", "Disabled-Model"
        );
        AgentState state = new AgentState(data);

        ModelConfig config = new ModelConfig();
        config.setEnabled(false);  // disabled!
        when(modelConfigService.findByDisplayName("Disabled-Model")).thenReturn(Optional.of(config));

        String json = """
                {
                  "refinedQuery": "query",
                  "route": "FALLBACK",
                  "routeConfidence": 0.5,
                  "keywords": [],
                  "subQuestions": [],
                  "reasoning": "out of scope"
                }
                """;
        stubChatClientToReturn(json);

        Map<String, Object> result = node.process(state);

        assertThat(result.get("route")).isEqualTo("FALLBACK");
        verify(chatClient).prompt(); // default client used
    }
}
