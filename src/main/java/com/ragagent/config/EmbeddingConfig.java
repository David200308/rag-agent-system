package com.ragagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

/**
 * Manually creates the {@link EmbeddingModel} used by Weaviate.
 *
 * Embedding provider is chosen by {@code EMBEDDING_PROVIDER} env var (falls back
 * to {@code LLM_PROVIDER} if unset). Anthropic has no embeddings API and falls
 * through to the OpenAI path.
 *
 *   local       → Ollama's OpenAI-compatible /v1/embeddings (no API key needed).
 *                 Default model: nomic-embed-text. Override with LOCAL_EMBEDDING_MODEL.
 *   openrouter  → OpenRouter /v1/embeddings using OPENROUTER_API_KEY.
 *                 Default model: openai/text-embedding-3-small. Override with OPENROUTER_EMBEDDING_MODEL.
 *   all others  → OpenAI text-embedding-3-small (requires OPENAI_API_KEY).
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class EmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(LlmProperties props) {
        // Use EMBEDDING_PROVIDER if set, otherwise fall back to LLM_PROVIDER.
        // OpenRouter and Anthropic do not expose an embeddings API, so they fall
        // through to the OpenAI embedding path. Use EMBEDDING_PROVIDER=local for Ollama.
        String embeddingProvider = StringUtils.hasText(props.getEmbeddingProvider())
                ? props.getEmbeddingProvider().toLowerCase()
                : props.getProvider().toLowerCase();

        return switch (embeddingProvider) {
            case "local"       -> buildLocalEmbedding(props.getLocal());
            case "openrouter"  -> buildOpenRouterEmbedding(props.getOpenrouter());
            default            -> buildOpenAiEmbedding(props.getOpenai());
        };
    }

    private EmbeddingModel buildOpenRouterEmbedding(LlmProperties.OpenRouterProps p) {
        log.info("[EmbeddingConfig] OpenRouter embedding: baseUrl={} model={}",
                p.getBaseUrl(), p.getEmbeddingModel());
        var api = OpenAiApi.builder()
                .baseUrl(p.getBaseUrl())
                .apiKey(p.getApiKey())
                .build();
        var options = OpenAiEmbeddingOptions.builder()
                .model(p.getEmbeddingModel())
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
    }

    private EmbeddingModel buildLocalEmbedding(LlmProperties.LocalProps p) {
        log.info("[EmbeddingConfig] Local embedding via Ollama: baseUrl={} model={}",
                p.getBaseUrl(), p.getEmbeddingModel());
        var api = OpenAiApi.builder()
                .baseUrl(p.getBaseUrl())
                .apiKey("local")   // Ollama ignores the key; must be non-null
                .build();
        var options = OpenAiEmbeddingOptions.builder()
                .model(p.getEmbeddingModel())
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
    }

    private EmbeddingModel buildOpenAiEmbedding(LlmProperties.OpenAiProps p) {
        log.info("[EmbeddingConfig] OpenAI embedding: model={}", p.getEmbeddingModel());
        var api = OpenAiApi.builder()
                .baseUrl(p.getBaseUrl())
                .apiKey(p.getApiKey())
                .build();
        var options = OpenAiEmbeddingOptions.builder()
                .model(p.getEmbeddingModel())
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
    }
}
