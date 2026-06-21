package com.ragagent.fallback;

import com.ragagent.config.ChatModelFactory;
import com.ragagent.config.FallbackProperties;
import com.ragagent.model.ModelConfig;
import com.ragagent.model.ModelConfigService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Fallback strategy hierarchy:
 *
 *   1. Cache hit        — return a previously computed answer for an identical query
 *   2. LLM direct       — answer from model knowledge without retrieval
 *   3. Static message   — last-resort message when the LLM itself is unavailable
 *
 * The circuit-breaker on the LLM call prevents cascading failures.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FallbackService {

    private final ChatClient          chatClient;
    private final ModelConfigService  modelConfigService;
    private final ChatModelFactory    chatModelFactory;
    private final StringRedisTemplate redisTemplate;
    private final FallbackProperties  fallbackProperties;

    private static final String CACHE_KEY_PREFIX = "fallback:answer-cache:";

    private static final String STATIC_FALLBACK =
            "I'm sorry, I'm unable to answer your question at the moment. " +
            "Please try rephrasing your query or contact support.";

    /**
     * Entry point called by {@link com.ragagent.agent.nodes.FallbackNode}.
     */
    public String resolveFallback(String query, String reason, Optional<String> selectedModelDisplayName) {
        log.warn("[FallbackService] Resolving fallback for: '{}', reason: {}", query, reason);

        // 1. Check cache
        String cached = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + normalise(query));
        if (cached != null) {
            log.info("[FallbackService] Cache hit for query");
            return "(Cached) " + cached;
        }

        // 2. Try LLM direct answer with circuit-breaker
        return tryDirectAnswer(query, reason, selectedModelDisplayName);
    }

    @CircuitBreaker(name = "llm", fallbackMethod = "staticFallback")
    public String tryDirectAnswer(String query, String reason, Optional<String> selectedModelDisplayName) {
        log.info("[FallbackService] Attempting direct LLM answer");

        ModelConfig selectedConfig = selectedModelDisplayName
                .flatMap(modelConfigService::findByDisplayName)
                .filter(ModelConfig::isEnabled)
                .orElse(null);
        ChatClient effectiveClient = selectedConfig != null
                ? chatModelFactory.buildChatClient(selectedConfig)
                : chatClient;

        String answer = effectiveClient.prompt()
                .system("""
                        You are a helpful assistant. Answer the user's question to the best
                        of your knowledge. If you cannot answer reliably, say so clearly.
                        Do NOT make up facts.
                        """)
                .user("Question: " + query)
                .call()
                .content();

        // Cache for future fallback hits
        redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + normalise(query), answer, cacheTtl());
        return answer;
    }

    /** Resilience4j fallback — LLM circuit-breaker is open. */
    public String staticFallback(String query, String reason, Optional<String> selectedModelDisplayName, Throwable ex) {
        log.error("[FallbackService] LLM unavailable, serving static fallback: {}", ex.getMessage());
        return STATIC_FALLBACK;
    }

    /** Cache a known good answer manually (e.g. from admin endpoint). */
    public void cacheAnswer(String query, String answer) {
        redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + normalise(query), answer, cacheTtl());
    }

    private String normalise(String query) {
        return query.trim().toLowerCase();
    }

    private Duration cacheTtl() {
        return Duration.ofMinutes(fallbackProperties.cacheTtlMinutes());
    }
}
