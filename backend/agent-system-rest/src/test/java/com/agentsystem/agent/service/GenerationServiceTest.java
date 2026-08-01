package com.agentsystem.agent.service;

import com.agentsystem.agent.service.impl.GenerationServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationServiceTest {

    @Mock ChatClient            chatClient;
    @Mock ToolCallbackProvider  tools;

    GenerationServiceImpl service = new GenerationServiceImpl();

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

    // Regression test for the bug where GeneratorNode set connector tools' ThreadLocal
    // user context on its own thread, but the actual LLM/tool-calling call runs on
    // GenerationServiceImpl's own worker thread — so that context was never visible to
    // the tools. contextSetup/contextCleanup must run on the same thread as the call.
    @Test
    void generate_success_runsContextSetupAndCleanupOnTheWorkerThread() throws ExecutionException, InterruptedException {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec      callSpec    = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(tools)).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Generated answer");

        AtomicBoolean setupRan = new AtomicBoolean(false);
        AtomicBoolean cleanupRan = new AtomicBoolean(false);
        AtomicReference<Thread> setupThread = new AtomicReference<>();
        Thread callingThread = Thread.currentThread();

        String answer = service.generate(chatClient, "system prompt", "user prompt", tools,
                () -> { setupRan.set(true); setupThread.set(Thread.currentThread()); },
                () -> cleanupRan.set(true)).get();

        assertThat(answer).isEqualTo("Generated answer");
        assertThat(setupRan).isTrue();
        assertThat(cleanupRan).isTrue();
        assertThat(setupThread.get()).isNotEqualTo(callingThread);
    }

    // ── generateFallback ──────────────────────────────────────────────────────

    @Test
    void generateFallback_returnsNullAnswerFuture() throws ExecutionException, InterruptedException {
        CompletableFuture<String> result = service.generateFallback(
                chatClient, "system prompt", "user prompt", tools, () -> {}, () -> {},
                new RuntimeException("provider down"));

        assertThat(result.get()).isNull();
    }
}
