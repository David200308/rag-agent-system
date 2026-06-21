package com.ragagent.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationServiceTest {

    @Mock ChatClient            chatClient;
    @Mock ToolCallbackProvider  tools;

    GenerationService service = new GenerationService();

    // ── generate ──────────────────────────────────────────────────────────────

    @Test
    void generate_success_returnsContent() throws ExecutionException, InterruptedException {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec      callSpec    = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system("system prompt")).thenReturn(requestSpec);
        when(requestSpec.user("user prompt")).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(tools)).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Generated answer");

        String answer = service.generate(chatClient, "system prompt", "user prompt", tools).get();

        assertThat(answer).isEqualTo("Generated answer");
    }

    // ── generateFallback ──────────────────────────────────────────────────────

    @Test
    void generateFallback_returnsNullAnswerFuture() throws ExecutionException, InterruptedException {
        CompletableFuture<String> result = service.generateFallback(
                chatClient, "system prompt", "user prompt", tools, new RuntimeException("provider down"));

        assertThat(result.get()).isNull();
    }
}
