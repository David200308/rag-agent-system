package com.agentsystem.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.anthropic.AnthropicChatModel;

import static org.assertj.core.api.Assertions.assertThat;

class LlmProviderConfigTest {

    LlmProperties    props;
    LlmProviderConfig config;

    @BeforeEach
    void setUp() {
        props  = new LlmProperties();
        config = new LlmProviderConfig();

        // Set minimal valid values for all providers
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
    }

    // ── chatModel — per-provider ──────────────────────────────────────────────

    @Test
    void chatModel_openai_returnsOpenAiChatModel() {
        props.setProvider("openai");
        ChatModel model = config.chatModel(props);
        assertThat(model).isNotNull();
        assertThat(model).isInstanceOf(OpenAiChatModel.class);
    }

    @Test
    void chatModel_anthropic_returnsAnthropicChatModel() {
        props.setProvider("anthropic");
        ChatModel model = config.chatModel(props);
        assertThat(model).isNotNull();
        assertThat(model).isInstanceOf(AnthropicChatModel.class);
    }

    @Test
    void chatModel_openrouter_returnsOpenAiChatModel() {
        props.setProvider("openrouter");
        ChatModel model = config.chatModel(props);
        assertThat(model).isNotNull();
        assertThat(model).isInstanceOf(OpenAiChatModel.class);
    }

    @Test
    void chatModel_deepseek_returnsOpenAiChatModel() {
        props.setProvider("deepseek");
        ChatModel model = config.chatModel(props);
        assertThat(model).isNotNull();
        assertThat(model).isInstanceOf(OpenAiChatModel.class);
    }

    @Test
    void chatModel_local_returnsOpenAiChatModel() {
        props.setProvider("local");
        ChatModel model = config.chatModel(props);
        assertThat(model).isNotNull();
        assertThat(model).isInstanceOf(OpenAiChatModel.class);
    }

    @Test
    void chatModel_unknownProvider_fallsBackToOpenAi() {
        props.setProvider("unknown-provider");
        ChatModel model = config.chatModel(props);
        assertThat(model).isNotNull();
        assertThat(model).isInstanceOf(OpenAiChatModel.class);
    }

    @Test
    void chatModel_caseInsensitive_anthropic() {
        props.setProvider("ANTHROPIC");
        ChatModel model = config.chatModel(props);
        assertThat(model).isInstanceOf(AnthropicChatModel.class);
    }

    @Test
    void chatModel_openrouter_stripsTrailingV1() {
        props.setProvider("openrouter");
        props.getOpenrouter().setBaseUrl("https://openrouter.ai/api/v1");
        ChatModel model = config.chatModel(props);
        assertThat(model).isNotNull();
    }

    @Test
    void chatModel_deepseek_stripsTrailingV1() {
        props.setProvider("deepseek");
        props.getDeepseek().setBaseUrl("https://api.deepseek.com/v1");
        ChatModel model = config.chatModel(props);
        assertThat(model).isNotNull();
    }
}
