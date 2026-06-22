package com.agentsystem.agent.service.impl;

import com.agentsystem.agent.service.GenerationService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hosts the actual LLM call so Resilience4j's circuit-breaker/retry/timeout can
 * intercept it through the Spring AOP proxy. GeneratorNode can't apply these
 * annotations to itself — a self-invocation bypasses the proxy entirely — so the
 * call is hosted here instead, the same way RetrievalService hosts RetrievalNode's
 * resilience-wrapped Weaviate call.
 *
 * @TimeLimiter only takes effect on methods returning CompletableFuture (Resilience4j
 * schedules the timeout against the future); a plain synchronous return type would
 * make the annotation a no-op, so the blocking ChatClient call runs on a virtual
 * thread and is wrapped in a future here.
 */
@Slf4j
@Service
public class GenerationServiceImpl implements GenerationService {

    private final ExecutorService asyncPool = Executors.newVirtualThreadPerTaskExecutor();

    @CircuitBreaker(name = "llm", fallbackMethod = "generateFallback")
    @Retry(name = "llm")
    @TimeLimiter(name = "llm")
    @Override
    public CompletableFuture<String> generate(ChatClient client, String systemPrompt,
                                               String userPrompt, ToolCallbackProvider tools) {
        return CompletableFuture.supplyAsync(() ->
                client.prompt()
                      .system(systemPrompt)
                      .user(userPrompt)
                      .toolCallbacks(tools)
                      .call()
                      .content(),
                asyncPool);
    }

    /**
     * Resilience4j fallback — returns a null answer so GeneratorNode routes the
     * graph to FallbackNode instead of surfacing a raw 500.
     */
    public CompletableFuture<String> generateFallback(ChatClient client, String systemPrompt,
                                                       String userPrompt, ToolCallbackProvider tools,
                                                       Throwable ex) {
        log.error("[GenerationService] Circuit-breaker fallback triggered: {}", ex.getMessage());
        return CompletableFuture.completedFuture(null);
    }
}
