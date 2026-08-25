package com.agentsystem.fallback.service;

import com.agentsystem.fallback.service.impl.FallbackServiceImpl;

import com.agentsystem.config.ChatModelFactory;
import com.agentsystem.config.FallbackProperties;
import com.agentsystem.model.service.ModelConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FallbackServiceTest {

    @Mock ChatClient                       chatClient;
    @Mock ModelConfigService               modelConfigService;
    @Mock ChatModelFactory                 chatModelFactory;
    @Mock StringRedisTemplate              redisTemplate;
    @Mock ValueOperations<String, String>  valueOperations;

    FallbackServiceImpl fallbackService;

    @BeforeEach
    void setUp() {
        // Fake StringRedisTemplate backed by a plain map — keeps the test a pure unit test, no Redis needed.
        Map<String, String> cacheBacking = new ConcurrentHashMap<>();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenAnswer(inv -> cacheBacking.get(inv.getArgument(0)));
        lenient().doAnswer(inv -> cacheBacking.put(inv.getArgument(0), inv.getArgument(1)))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        fallbackService = new FallbackServiceImpl(chatClient, modelConfigService, chatModelFactory, redisTemplate,
                new FallbackProperties(60));
    }

    // ── cacheAnswer & cache-hit path ───────────────────────────────────────────

    @Test
    void resolveFallback_cacheHit_returnsCachedAnswerWithPrefix() {
        fallbackService.cacheAnswer("hello world", "Hi there!");

        String result = fallbackService.resolveFallback("hello world", "out-of-scope", Optional.empty());

        assertThat(result).startsWith("(Cached) ");
        assertThat(result).contains("Hi there!");
    }

    @Test
    void resolveFallback_cacheHit_normalisesQueryCaseAndWhitespace() {
        fallbackService.cacheAnswer("  hello world  ", "Hi!");

        // normalise() trims + lowercases the key
        String result = fallbackService.resolveFallback("HELLO WORLD", "reason", Optional.empty());

        assertThat(result).startsWith("(Cached)");
    }

    @Test
    void cacheAnswer_setsTtlFromConfiguredMinutes() {
        fallbackService.cacheAnswer("ttl query", "answer");

        verify(valueOperations).set(eq("fallback:answer-cache:ttl query"), eq("answer"), eq(Duration.ofMinutes(60)));
    }

    @Test
    void tryDirectAnswer_cachesWithTtlFromConfiguredMinutes() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Direct answer");

        fallbackService.tryDirectAnswer("ttl direct query", "no cache", Optional.empty());

        verify(valueOperations).set(eq("fallback:answer-cache:ttl direct query"), eq("Direct answer"),
                eq(Duration.ofMinutes(60)));
    }

    @Test
    void cacheAnswer_overwritesPreviousEntry() {
        fallbackService.cacheAnswer("key query", "First");
        fallbackService.cacheAnswer("key query", "Second");

        String result = fallbackService.resolveFallback("key query", "reason", Optional.empty());

        assertThat(result).contains("Second");
        assertThat(result).doesNotContain("First");
    }

    // ── staticFallback ─────────────────────────────────────────────────────────

    @Test
    void staticFallback_alwaysReturnsStaticMessage() {
        String result = fallbackService.staticFallback(
                "any query", "LLM circuit open", Optional.empty(),
                new RuntimeException("breaker tripped"));

        assertThat(result).contains("unable to answer");
    }

    @Test
    void staticFallback_withSelectedModel_stillReturnsStaticMessage() {
        String result = fallbackService.staticFallback(
                "any query", "timeout", Optional.of("gpt-4"),
                new RuntimeException("connection refused"));

        assertThat(result).contains("unable to answer");
    }

    // ── tryDirectAnswer ───────────────────────────────────────────────────────

    @Test
    void tryDirectAnswer_noSelectedModel_callsDefaultClient() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Direct LLM answer");

        String result = fallbackService.tryDirectAnswer("test query", "no cache", Optional.empty());

        assertThat(result).isEqualTo("Direct LLM answer");
    }

    @Test
    void tryDirectAnswer_cachesAnswerAfterCall() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Cached Direct Answer");

        // First call populates the cache
        fallbackService.tryDirectAnswer("unique query xyz", "no cache", Optional.empty());

        // Second call via resolveFallback should return cached result
        String cached = fallbackService.resolveFallback("unique query xyz", "reason", Optional.empty());

        assertThat(cached).startsWith("(Cached) ");
        assertThat(cached).contains("Cached Direct Answer");
    }
}
