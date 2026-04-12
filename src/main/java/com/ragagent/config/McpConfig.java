package com.ragagent.config;

import com.ragagent.mcp.RagMcpService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Registers RAG tools with the Spring AI MCP server.
 *
 * The MCP server auto-configuration (spring-ai-mcp-server-webmvc-spring-boot-starter)
 * picks up all {@link ToolCallbackProvider} beans and exposes them at:
 *
 *   GET  http://localhost:8081/mcp/sse   — SSE event stream (MCP client connects here)
 *   POST http://localhost:8081/mcp/message — tool call handler
 *
 * To connect Claude Desktop, add to claude_desktop_config.json:
 * {
 *   "mcpServers": {
 *     "rag-agent": {
 *       "url": "http://localhost:8081/mcp/sse"
 *     }
 *   }
 * }
 */
@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider ragToolCallbackProvider(RagMcpService ragMcpService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(ragMcpService)
                .build();
    }

    /** Shared RestClient.Builder for URL fetching in McpConnectorService. */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
