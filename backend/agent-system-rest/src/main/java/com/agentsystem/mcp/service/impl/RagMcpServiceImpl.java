package com.agentsystem.mcp.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import com.agentsystem.config.McpProperties;
import com.agentsystem.knowledge.entity.KnowledgeSource;
import com.agentsystem.knowledge.service.KnowledgeSourceService;
import com.agentsystem.mcp.service.RagMcpService;
import com.agentsystem.rag.service.RetrievalService;
import com.agentsystem.schema.DocumentResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP-exposed tools for the Agent System system.
 *
 * Registered as a ToolCallbackProvider via {@link McpConfig}.
 * MCP clients (Claude Desktop, Claude Code, etc.) can connect to:
 *
 *   http://localhost:8081/mcp/sse
 *
 * Available tools:
 *   - search_knowledge  — semantic search over the Weaviate knowledge base
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagMcpServiceImpl implements RagMcpService {

    private final RetrievalService      retrievalService;
    private final KnowledgeSourceService knowledgeSourceService;
    private final McpProperties         mcpProperties;

    /**
     * Semantic search over the Weaviate knowledge base.
     *
     * @param query    natural-language question or keyword
     * @param topK     maximum number of results to return (default 5)
     */
    @Tool(description = "Search the RAG knowledge base for information relevant to a query. Returns matching document excerpts with source metadata.")
    @Override
    public String searchKnowledge(String query, int topK) {
        int k = topK <= 0 ? 5 : Math.min(topK, 20);
        log.debug("[RagMcpService] MCP tool searchKnowledge query='{}' topK={}", query, k);

        // MCP carries no per-request JWT (gated only by McpAuthFilter's shared key), so
        // every MCP caller is scoped to mcp.email's accessible sources — same boundary
        // RetrievalNode applies for normal chat. Unconfigured mcp.email denies all
        // sources rather than falling back to KnowledgeSourceService's "email == null"
        // unrestricted-access behavior.
        String scopedEmail = mcpProperties.email();
        Set<String> allowedSources;
        if (scopedEmail == null || scopedEmail.isBlank()) {
            log.warn("[RagMcpService] mcp.email is not configured — search_knowledge denies all sources");
            allowedSources = Set.of();
        } else {
            allowedSources = knowledgeSourceService.listAccessible(scopedEmail).stream()
                    .map(KnowledgeSource::getSource)
                    .collect(Collectors.toSet());
        }

        List<DocumentResult> results = retrievalService.retrieve(query, k, Map.of(), allowedSources);
        if (results.isEmpty()) {
            return "No relevant documents found for: " + query;
        }

        return results.stream()
                .map(r -> String.format(
                        "Source: %s (score=%.2f)\n%s",
                        r.source(), r.score(), r.content()))
                .collect(Collectors.joining("\n\n---\n\n"));
    }
}
