package com.agentsystem.config;

import com.agentsystem.model.entity.ModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds and caches {@link ChatClient} instances for user-selected model configurations.
 *
 * API keys are sourced from {@link LlmProperties} per platform, so each platform
 * shares one key while multiple model IDs (e.g. gpt-4o, gpt-4o-mini) can be offered.
 *
 * Cache is keyed by displayName. If a ModelConfig is updated, restart the app to
 * pick up the new model ID (or evict via {@link #evict(String)}).
 */
@Slf4j
@Component
public class ChatModelFactory {

    private final LlmProperties props;
    private final ConcurrentHashMap<String, ChatModel> cache = new ConcurrentHashMap<>();

    public ChatModelFactory(LlmProperties props) {
        this.props = props;
    }

    public ChatClient buildChatClient(ModelConfig config) {
        ChatModel model = cache.computeIfAbsent(config.getDisplayName(), k -> buildModel(config));
        return ChatClient.builder(model).build();
    }

    public void evict(String displayName) {
        cache.remove(displayName);
    }

    private ChatModel buildModel(ModelConfig config) {
        log.info("[ChatModelFactory] Building ChatModel displayName='{}' platform='{}' modelId='{}'",
                config.getDisplayName(), config.getPlatform(), config.getModelId());
        return switch (config.getPlatform().toLowerCase()) {
            case "anthropic"  -> buildAnthropic(config.getModelId());
            case "openrouter" -> buildOpenRouter(config.getModelId());
            case "local"      -> buildLocal(config.getModelId());
            case "deepseek"   -> buildDeepSeek(config.getModelId());
            default           -> buildOpenAi(config.getModelId());
        };
    }

    private ChatModel buildOpenAi(String modelId) {
        var p = props.getOpenai();
        var api = OpenAiApi.builder()
                .baseUrl(p.getBaseUrl())
                .apiKey(p.getApiKey())
                .build();
        var options = OpenAiChatOptions.builder()
                .model(modelId)
                .temperature(p.getTemperature())
                .build();
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
    }

    private ChatModel buildAnthropic(String modelId) {
        var p = props.getAnthropic();
        var api = AnthropicApi.builder()
                .apiKey(p.getApiKey())
                .build();
        var options = AnthropicChatOptions.builder()
                .model(modelId)
                .maxTokens(p.getMaxTokens())
                .temperature(p.getTemperature())
                .build();
        return AnthropicChatModel.builder().anthropicApi(api).defaultOptions(options).build();
    }

    private ChatModel buildOpenRouter(String modelId) {
        var p = props.getOpenrouter();
        var extraHeaders = new LinkedMultiValueMap<String, String>();
        extraHeaders.add("HTTP-Referer", p.getSiteUrl());
        extraHeaders.add("X-Title", p.getSiteName());
        String baseUrl = p.getBaseUrl().replaceAll("/v1$", "");
        var api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(p.getApiKey())
                .headers(extraHeaders)
                .build();
        var options = OpenAiChatOptions.builder()
                .model(modelId)
                .temperature(p.getTemperature())
                .build();
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
    }

    private ChatModel buildDeepSeek(String modelId) {
        var p = props.getDeepseek();
        String baseUrl = p.getBaseUrl().replaceAll("/v1$", "");
        var api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(p.getApiKey())
                .build();
        var options = OpenAiChatOptions.builder()
                .model(modelId)
                .temperature(p.getTemperature())
                .build();
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
    }

    private ChatModel buildLocal(String modelId) {
        var p = props.getLocal();
        var api = OpenAiApi.builder()
                .baseUrl(p.getBaseUrl())
                .apiKey("local")
                .build();
        var options = OpenAiChatOptions.builder()
                .model(modelId)
                .temperature(p.getTemperature())
                .build();
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
    }
}
