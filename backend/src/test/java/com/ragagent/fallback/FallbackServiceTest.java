package com.ragagent.fallback;

import com.ragagent.config.ChatModelFactory;
import com.ragagent.model.ModelConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class FallbackServiceTest {

    @Mock ChatClient         chatClient;
    @Mock ModelConfigService modelConfigService;
    @Mock ChatModelFactory   chatModelFactory;

    FallbackService fallbackService;

    @BeforeEach
    void setUp() {
        fallbackService = new FallbackService(chatClient, modelConfigService, chatModelFactory);
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
}
