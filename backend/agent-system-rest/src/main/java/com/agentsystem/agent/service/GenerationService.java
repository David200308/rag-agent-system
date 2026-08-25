package com.agentsystem.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.concurrent.CompletableFuture;

public interface GenerationService {

    /**
     * @param contextSetup   run on the actual worker thread immediately before the LLM call,
     *                       so any ThreadLocal-scoped tool context (user uuid, org id, ...) it
     *                       sets is visible to tool invocations the LLM triggers during the call —
     *                       setting such context on the caller's thread instead has no effect,
     *                       since the call itself runs on a different thread (see impl).
     * @param contextCleanup run on that same worker thread after the call completes
     */
    CompletableFuture<String> generate(ChatClient client, String systemPrompt, String userPrompt,
                                        ToolCallbackProvider tools, Runnable contextSetup, Runnable contextCleanup);

    default CompletableFuture<String> generate(ChatClient client, String systemPrompt,
                                                String userPrompt, ToolCallbackProvider tools) {
        return generate(client, systemPrompt, userPrompt, tools, () -> {}, () -> {});
    }
}
