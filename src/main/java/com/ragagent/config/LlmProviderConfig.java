package com.ragagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.LinkedMultiValueMap;

/**
 * Creates exactly one {@link ChatModel} bean — selected by {@code llm.provider}.
 *
 * Why manual construction instead of Spring AI auto-config?
 * Both OpenAI and Anthropic starters are on the classpath so Spring AI would
 * create BOTH chat models and fail on missing API keys. We disable their chat
 * auto-config in application.yml ({@code spring.ai.*.chat.enabled=false}) and
 * build only the requested one here.
 *
 * The OpenAI EmbeddingModel (used by Weaviate) is unaffected — it is still
 * auto-configured by the OpenAI starter.
 *
 * Supported providers:
 *   openai      → OpenAiChatModel  (api.openai.com)
 *   anthropic   → AnthropicChatModel (api.anthropic.com)
 *   openrouter  → OpenAiChatModel with openrouter.ai base URL + extra headers
 *   local       → OpenAiChatModel with local server base URL (Ollama / LM Studio)
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmProviderConfig {

    @Bean
    @Primary
    public ChatModel chatModel(LlmProperties props) {
        String provider = props.getProvider();
        log.info("[LlmProviderConfig] Configuring ChatModel for provider: {}", provider);

        return switch (provider.toLowerCase()) {
            case "anthropic"  -> buildAnthropic(props.getAnthropic());
            case "openrouter" -> buildOpenRouter(props.getOpenrouter());
            case "local"      -> buildLocal(props.getLocal());
            default           -> buildOpenAi(props.getOpenai());
        };
    }

    // ── OpenAI ──────────────────────────────────────────────────────────────

    private ChatModel buildOpenAi(LlmProperties.OpenAiProps p) {
        log.info("[LlmProviderConfig] OpenAI model={}", p.getModel());
        var api = OpenAiApi.builder()
                .baseUrl(p.getBaseUrl())
                .apiKey(p.getApiKey())
                .build();
        var options = OpenAiChatOptions.builder()
                .model(p.getModel())
                .temperature((double) p.getTemperature())
                .build();
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
    }

    // ── Anthropic Claude ─────────────────────────────────────────────────────

    private ChatModel buildAnthropic(LlmProperties.AnthropicProps p) {
        log.info("[LlmProviderConfig] Anthropic model={}", p.getModel());
        var api = AnthropicApi.builder()
                .apiKey(p.getApiKey())
                .build();
        var options = AnthropicChatOptions.builder()
                .model(p.getModel())
                .maxTokens(p.getMaxTokens())
                .temperature((double) p.getTemperature())
                .build();
        return AnthropicChatModel.builder().anthropicApi(api).defaultOptions(options).build();
    }

    // ── OpenRouter (OpenAI-compatible endpoint) ───────────────────────────────

    private ChatModel buildOpenRouter(LlmProperties.OpenRouterProps p) {
        log.info("[LlmProviderConfig] OpenRouter model={}", p.getModel());

        // OpenRouter requires two additional HTTP headers for attribution / analytics.
        var extraHeaders = new LinkedMultiValueMap<String, String>();
        extraHeaders.add("HTTP-Referer", p.getSiteUrl());
        extraHeaders.add("X-Title", p.getSiteName());

        // Spring AI's OpenAiApi appends /v1/chat/completions to baseUrl, so strip any
        // trailing /v1 to avoid a double /v1 path (e.g. openrouter.ai/api/v1/v1/chat/completions).
        String baseUrl = p.getBaseUrl().replaceAll("/v1$", "");
        log.info("[LlmProviderConfig] OpenRouter baseUrl (normalized)={}", baseUrl);

        var api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(p.getApiKey())
                .headers(extraHeaders)
                .build();

        var options = OpenAiChatOptions.builder()
                .model(p.getModel())
                .temperature((double) p.getTemperature())
                .build();

        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
    }

    // ── Local LLM (Ollama / LM Studio / llama.cpp) ───────────────────────────
    // These servers expose an OpenAI-compatible /v1 endpoint with no auth needed.

    private ChatModel buildLocal(LlmProperties.LocalProps p) {
        log.info("[LlmProviderConfig] Local LLM baseUrl={} model={}", p.getBaseUrl(), p.getModel());
        var api = OpenAiApi.builder()
                .baseUrl(p.getBaseUrl())
                .apiKey("local")   // key unused but must be non-null
                .build();
        var options = OpenAiChatOptions.builder()
                .model(p.getModel())
                .temperature((double) p.getTemperature())
                .build();
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
    }
}
