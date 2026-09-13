package com.agentsystem.agent.service.impl;

import com.agentsystem.agent.service.GenerationService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
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
 *
 * Because the call runs on {@code asyncPool} rather than the caller's thread, any
 * ThreadLocal-scoped context the caller set beforehand (e.g. GeneratorNode priming
 * the connector tools' per-request user uuid) is invisible to the tool invocations
 * the LLM triggers during this call — ThreadLocal doesn't cross threads. contextSetup
 * is run here, on the actual worker thread, for exactly that reason.
 */
@Slf4j
@Service
public class GenerationServiceImpl implements GenerationService {

    private final ExecutorService asyncPool = Executors.newVirtualThreadPerTaskExecutor();

    @CircuitBreaker(name = "llm", fallbackMethod = "generateFallback")
    @Retry(name = "llm")
    @TimeLimiter(name = "llm")
    @Override
    public CompletableFuture<String> generate(ChatClient client, String systemPrompt, String userPrompt,
                                               ToolCallbackProvider tools, Runnable contextSetup,
                                               Runnable contextCleanup) {
        return CompletableFuture.supplyAsync(() -> {
            contextSetup.run();
            try {
                try {
                    return client.prompt()
                            .system(systemPrompt)
                            .user(userPrompt)
                            .toolCallbacks(tools)
                            .call()
                            .content();
                } catch (Exception ex) {
                    if (!isOpenRouterNoToolEndpoint(ex)) {
                        throw ex;
                    }
                    // OpenRouter filtered every provider endpoint out for lacking tool-calling
                    // support (e.g. meta-llama/llama-4-scout) — retry once without tools rather
                    // than failing the whole request / tripping the circuit breaker.
                    log.warn("[GenerationService] Model has no tool-compatible OpenRouter endpoint; "
                            + "retrying without tools");
                    return client.prompt()
                            .system(systemPrompt)
                            .user(userPrompt)
                            .call()
                            .content();
                }
            } finally {
                contextCleanup.run();
            }
        }, asyncPool);
    }

    /**
     * Detects OpenRouter's "no endpoints found" 404, which fires when the requested model has no
     * provider endpoint supporting tool-calling and a non-empty {@code tools} array was sent.
     * Spring AI's default {@code ResponseErrorHandler} collapses the HTTP response into a plain
     * {@link NonTransientAiException} with no status-code accessor, so detection has to match the
     * "{status} - {body}" message format it builds (confirmed against the OpenRouter error body:
     * {@code 404 - {"error":{"message":"No endpoints found for ...","code":404,...}}}).
     */
    private static boolean isOpenRouterNoToolEndpoint(Exception ex) {
        String msg = ex.getMessage();
        return ex instanceof NonTransientAiException
                && msg != null
                && msg.startsWith("404 - ")
                && msg.contains("No endpoints found");
    }

    /**
     * Resilience4j fallback — returns a null answer so GeneratorNode routes the
     * graph to FallbackNode instead of surfacing a raw 500.
     */
    public CompletableFuture<String> generateFallback(ChatClient client, String systemPrompt, String userPrompt,
                                                       ToolCallbackProvider tools, Runnable contextSetup,
                                                       Runnable contextCleanup, Throwable ex) {
        log.error("[GenerationService] Circuit-breaker fallback triggered: {}", ex.getMessage());
        return CompletableFuture.completedFuture(null);
    }
}
