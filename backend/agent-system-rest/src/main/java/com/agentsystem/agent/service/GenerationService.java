package com.agentsystem.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.concurrent.CompletableFuture;

public interface GenerationService {

    CompletableFuture<String> generate(ChatClient client, String systemPrompt,
                                        String userPrompt, ToolCallbackProvider tools);
}
