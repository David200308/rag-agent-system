package com.ragagent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI + OpenAPI configuration.
 *
 * {@link ChatModel} is injected here from {@link LlmProviderConfig}, which is
 * the single bean that selects openai / anthropic / openrouter at startup.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class AgentConfig {

    /**
     * Shared {@link ChatClient} used across all agent nodes.
     *
     * Explicitly wired to the {@code @Primary ChatModel} from
     * {@link LlmProviderConfig} so the active provider is always used,
     * regardless of which Spring AI starters are on the classpath.
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel, LlmProperties props) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(
                        MessageWindowChatMemory.builder()
                                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                                .build()).build())
                .build();
    }

    @Bean
    public OpenAPI openAPI(LlmProperties props) {
        return new OpenAPI().info(new Info()
                .title("RAG Agent System")
                .version("1.0.0")
                .description("""
                        Spring AI + LangGraph4j + Weaviate RAG agent.
                        Active LLM provider: **%s**
                        Structured-output validation via BeanOutputConverter (Java's Pydantic).
                        Fallback chain: cache → direct LLM → static message (Resilience4j).
                        """.formatted(props.getProvider())));
    }
}
