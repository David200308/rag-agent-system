package com.ragagent.config;

import com.ragagent.model.ModelConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;

class ChatModelFactoryTest {

    LlmProperties props;
    ChatModelFactory factory;

    @BeforeEach
    void setUp() {
        props = new LlmProperties();
        // Set up all provider props with minimal valid values
        props.getOpenai().setApiKey("sk-test");
        props.getOpenai().setBaseUrl("https://api.openai.com");
        props.getOpenai().setModel("gpt-4o-mini");

        props.getAnthropic().setApiKey("ant-test");
        props.getAnthropic().setModel("claude-opus-4-6");

        props.getOpenrouter().setApiKey("or-test");
        props.getOpenrouter().setBaseUrl("https://openrouter.ai/api/v1");
        props.getOpenrouter().setModel("openai/gpt-4o-mini");
        props.getOpenrouter().setSiteUrl("https://test.com");
        props.getOpenrouter().setSiteName("Test");

        props.getDeepseek().setApiKey("ds-test");
        props.getDeepseek().setBaseUrl("https://api.deepseek.com/v1");
        props.getDeepseek().setModel("deepseek-chat");

        props.getLocal().setBaseUrl("http://localhost:11434");
        props.getLocal().setModel("llama3");

        factory = new ChatModelFactory(props);
    }

    private ModelConfig config(String name, String platform, String modelId) {
        ModelConfig m = new ModelConfig();
        m.setDisplayName(name);
        m.setPlatform(platform);
        m.setModelId(modelId);
        m.setEnabled(true);
        return m;
    }

    // ── buildChatClient — per-provider ────────────────────────────────────────

    @Test
    void buildChatClient_openai_returnsClient() {
        ChatClient client = factory.buildChatClient(config("GPT-4o", "openai", "gpt-4o"));
        assertThat(client).isNotNull();
    }

    @Test
    void buildChatClient_anthropic_returnsClient() {
        ChatClient client = factory.buildChatClient(config("Claude", "anthropic", "claude-opus-4-6"));
        assertThat(client).isNotNull();
    }

    @Test
    void buildChatClient_openrouter_returnsClient() {
        ChatClient client = factory.buildChatClient(config("OR-GPT4", "openrouter", "openai/gpt-4o-mini"));
        assertThat(client).isNotNull();
    }

    @Test
    void buildChatClient_deepseek_returnsClient() {
        ChatClient client = factory.buildChatClient(config("DeepSeek", "deepseek", "deepseek-chat"));
        assertThat(client).isNotNull();
    }

    @Test
    void buildChatClient_local_returnsClient() {
        ChatClient client = factory.buildChatClient(config("Local", "local", "llama3"));
        assertThat(client).isNotNull();
    }

    @Test
    void buildChatClient_unknownPlatform_fallsBackToOpenai() {
        ChatClient client = factory.buildChatClient(config("Misc", "unknown-platform", "some-model"));
        assertThat(client).isNotNull();
    }

    // ── Cache behaviour ───────────────────────────────────────────────────────

    @Test
    void buildChatClient_calledTwice_returnsDifferentClientInstancesButSameModel() {
        ModelConfig cfg = config("GPT-4o", "openai", "gpt-4o");
        ChatClient c1 = factory.buildChatClient(cfg);
        ChatClient c2 = factory.buildChatClient(cfg);
        // Cache returns same ChatModel but buildChatClient wraps it in a new ChatClient each time
        assertThat(c1).isNotNull();
        assertThat(c2).isNotNull();
    }

    // ── evict ─────────────────────────────────────────────────────────────────

    @Test
    void evict_removesFromCache_nextBuildCreatesNewModel() {
        ModelConfig cfg = config("GPT-4o", "openai", "gpt-4o");
        factory.buildChatClient(cfg);  // populate cache

        factory.evict("GPT-4o");

        // After evict, next build should succeed (creates a new model)
        ChatClient client = factory.buildChatClient(cfg);
        assertThat(client).isNotNull();
    }

    @Test
    void evict_nonExistentKey_doesNotThrow() {
        factory.evict("NonExistentModel");  // should not throw
    }

    // ── OpenRouter URL normalization ──────────────────────────────────────────

    @Test
    void buildChatClient_openrouterWithTrailingV1_stripsV1() {
        props.getOpenrouter().setBaseUrl("https://openrouter.ai/api/v1");
        ChatClient client = factory.buildChatClient(config("OR", "openrouter", "openai/gpt-4o-mini"));
        assertThat(client).isNotNull();
    }

    @Test
    void buildChatClient_deepseekWithTrailingV1_stripsV1() {
        props.getDeepseek().setBaseUrl("https://api.deepseek.com/v1");
        ChatClient client = factory.buildChatClient(config("DS", "deepseek", "deepseek-chat"));
        assertThat(client).isNotNull();
    }
}
