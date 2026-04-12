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

/**
 * Manually creates the {@link EmbeddingModel} used by Weaviate.
 *
 * Spring AI's OpenAI embedding auto-config is disabled in application.yml
 * ({@code spring.ai.openai.embedding.enabled=false}) so we can choose the
 * right endpoint based on {@code llm.provider}:
 *
 *   local      → Ollama's OpenAI-compatible /v1/embeddings (no API key needed).
 *                Default embedding model: nomic-embed-text
 *                Override with LOCAL_EMBEDDING_MODEL env var.
 *   all others → OpenAI text-embedding-3-small (requires OPENAI_API_KEY).
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class EmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(LlmProperties props) {
        if ("local".equalsIgnoreCase(props.getProvider())) {
            return buildLocalEmbedding(props.getLocal());
        }
        return buildOpenAiEmbedding(props.getOpenai());
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
        log.info("[EmbeddingConfig] OpenAI embedding: model=text-embedding-3-small");
        var api = OpenAiApi.builder()
                .baseUrl(p.getBaseUrl())
                .apiKey(p.getApiKey())
                .build();
        var options = OpenAiEmbeddingOptions.builder()
                .model("text-embedding-3-small")
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
    }
}
