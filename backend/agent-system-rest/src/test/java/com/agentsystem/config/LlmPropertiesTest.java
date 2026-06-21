package com.ragagent.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPropertiesTest {

    // ── LlmProperties root ────────────────────────────────────────────────────

    @Test
    void defaultProvider_isOpenai() {
        assertThat(new LlmProperties().getProvider()).isEqualTo("openai");
    }

    @Test
    void setProvider_updatesValue() {
        LlmProperties p = new LlmProperties();
        p.setProvider("anthropic");
        assertThat(p.getProvider()).isEqualTo("anthropic");
    }

    @Test
    void defaultModel_isNullInitially() {
        assertThat(new LlmProperties().getDefaultModel()).isNull();
    }

    @Test
    void setDefaultModel_updatesValue() {
        LlmProperties p = new LlmProperties();
        p.setDefaultModel("GPT-4o");
        assertThat(p.getDefaultModel()).isEqualTo("GPT-4o");
    }

    @Test
    void embeddingProvider_isNullInitially() {
        assertThat(new LlmProperties().getEmbeddingProvider()).isNull();
    }

    @Test
    void setEmbeddingProvider_updatesValue() {
        LlmProperties p = new LlmProperties();
        p.setEmbeddingProvider("local");
        assertThat(p.getEmbeddingProvider()).isEqualTo("local");
    }

    @Test
    void nestedProps_areNotNull() {
        LlmProperties p = new LlmProperties();
        assertThat(p.getOpenai()).isNotNull();
        assertThat(p.getAnthropic()).isNotNull();
        assertThat(p.getOpenrouter()).isNotNull();
        assertThat(p.getLocal()).isNotNull();
        assertThat(p.getDeepseek()).isNotNull();
    }

    @Test
    void setNestedProps_updatesValue() {
        LlmProperties p = new LlmProperties();
        var oai = new LlmProperties.OpenAiProps();
        p.setOpenai(oai);
        assertThat(p.getOpenai()).isSameAs(oai);
    }

    // ── OpenAiProps ───────────────────────────────────────────────────────────

    @Test
    void openAiProps_defaults() {
        var p = new LlmProperties.OpenAiProps();
        assertThat(p.getApiKey()).isEqualTo("");
        assertThat(p.getBaseUrl()).isEqualTo("https://api.openai.com");
        assertThat(p.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(p.getEmbeddingModel()).isEqualTo("text-embedding-3-small");
        assertThat(p.getTemperature()).isEqualTo(0.1);
    }

    @Test
    void openAiProps_setters() {
        var p = new LlmProperties.OpenAiProps();
        p.setApiKey("sk-key");
        p.setBaseUrl("https://custom.openai.com");
        p.setModel("gpt-4o");
        p.setEmbeddingModel("text-embedding-ada-002");
        p.setTemperature(0.7);

        assertThat(p.getApiKey()).isEqualTo("sk-key");
        assertThat(p.getBaseUrl()).isEqualTo("https://custom.openai.com");
        assertThat(p.getModel()).isEqualTo("gpt-4o");
        assertThat(p.getEmbeddingModel()).isEqualTo("text-embedding-ada-002");
        assertThat(p.getTemperature()).isEqualTo(0.7);
    }

    // ── AnthropicProps ────────────────────────────────────────────────────────

    @Test
    void anthropicProps_defaults() {
        var p = new LlmProperties.AnthropicProps();
        assertThat(p.getApiKey()).isEqualTo("");
        assertThat(p.getBaseUrl()).isEqualTo("https://api.anthropic.com");
        assertThat(p.getModel()).isEqualTo("claude-opus-4-6");
        assertThat(p.getMaxTokens()).isEqualTo(8096);
        assertThat(p.getTemperature()).isEqualTo(0.1);
    }

    @Test
    void anthropicProps_setters() {
        var p = new LlmProperties.AnthropicProps();
        p.setApiKey("ant-key");
        p.setBaseUrl("https://custom.anthropic.com");
        p.setModel("claude-3-haiku");
        p.setMaxTokens(4096);
        p.setTemperature(0.5);

        assertThat(p.getApiKey()).isEqualTo("ant-key");
        assertThat(p.getBaseUrl()).isEqualTo("https://custom.anthropic.com");
        assertThat(p.getModel()).isEqualTo("claude-3-haiku");
        assertThat(p.getMaxTokens()).isEqualTo(4096);
        assertThat(p.getTemperature()).isEqualTo(0.5);
    }

    // ── OpenRouterProps ───────────────────────────────────────────────────────

    @Test
    void openRouterProps_defaults() {
        var p = new LlmProperties.OpenRouterProps();
        assertThat(p.getApiKey()).isEqualTo("");
        assertThat(p.getBaseUrl()).isEqualTo("https://openrouter.ai/api/v1");
        assertThat(p.getModel()).isEqualTo("openai/gpt-4o-mini");
        assertThat(p.getEmbeddingModel()).isEqualTo("openai/text-embedding-3-small");
        assertThat(p.getTemperature()).isEqualTo(0.1);
        assertThat(p.getSiteUrl()).isEqualTo("");
        assertThat(p.getSiteName()).isEqualTo("rag-agent-system");
    }

    @Test
    void openRouterProps_setters() {
        var p = new LlmProperties.OpenRouterProps();
        p.setApiKey("or-key");
        p.setBaseUrl("https://openrouter.ai/api/v1");
        p.setModel("anthropic/claude-3-haiku");
        p.setEmbeddingModel("openai/text-embedding-3-small");
        p.setTemperature(0.3);
        p.setSiteUrl("https://myapp.com");
        p.setSiteName("MyApp");

        assertThat(p.getApiKey()).isEqualTo("or-key");
        assertThat(p.getModel()).isEqualTo("anthropic/claude-3-haiku");
        assertThat(p.getSiteUrl()).isEqualTo("https://myapp.com");
        assertThat(p.getSiteName()).isEqualTo("MyApp");
    }

    // ── DeepSeekProps ─────────────────────────────────────────────────────────

    @Test
    void deepSeekProps_defaults() {
        var p = new LlmProperties.DeepSeekProps();
        assertThat(p.getApiKey()).isEqualTo("");
        assertThat(p.getBaseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(p.getModel()).isEqualTo("deepseek-chat");
        assertThat(p.getTemperature()).isEqualTo(0.1);
    }

    @Test
    void deepSeekProps_setters() {
        var p = new LlmProperties.DeepSeekProps();
        p.setApiKey("ds-key");
        p.setBaseUrl("https://api.deepseek.com/v1");
        p.setModel("deepseek-reasoner");
        p.setTemperature(0.0);

        assertThat(p.getApiKey()).isEqualTo("ds-key");
        assertThat(p.getBaseUrl()).isEqualTo("https://api.deepseek.com/v1");
        assertThat(p.getModel()).isEqualTo("deepseek-reasoner");
        assertThat(p.getTemperature()).isEqualTo(0.0);
    }

    // ── LocalProps ────────────────────────────────────────────────────────────

    @Test
    void localProps_defaults() {
        var p = new LlmProperties.LocalProps();
        assertThat(p.getBaseUrl()).isEqualTo("http://localhost:11434");
        assertThat(p.getModel()).isEqualTo("llama3");
        assertThat(p.getEmbeddingModel()).isEqualTo("nomic-embed-text");
        assertThat(p.getTemperature()).isEqualTo(0.1);
    }

    @Test
    void localProps_setters() {
        var p = new LlmProperties.LocalProps();
        p.setBaseUrl("http://localhost:1234");
        p.setModel("mistral");
        p.setEmbeddingModel("all-minilm");
        p.setTemperature(0.2);

        assertThat(p.getBaseUrl()).isEqualTo("http://localhost:1234");
        assertThat(p.getModel()).isEqualTo("mistral");
        assertThat(p.getEmbeddingModel()).isEqualTo("all-minilm");
        assertThat(p.getTemperature()).isEqualTo(0.2);
    }
}
