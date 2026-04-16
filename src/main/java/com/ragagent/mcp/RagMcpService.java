package com.ragagent.mcp;

import com.ragagent.rag.RetrievalService;
import com.ragagent.schema.DocumentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP-exposed tools for the RAG agent system.
 *
 * Registered as a ToolCallbackProvider via {@link McpConfig}.
 * MCP clients (Claude Desktop, Claude Code, etc.) can connect to:
 *
 *   http://localhost:8081/mcp/sse
 *
 * Available tools:
 *   - search_knowledge  — semantic search over the Weaviate knowledge base
 *   - ingest_url        — fetch a URL and add its content to the knowledge base
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagMcpService {

    private final RetrievalService    retrievalService;
    private final McpConnectorService mcpConnectorService;

    /**
     * Semantic search over the Weaviate knowledge base.
     *
     * @param query    natural-language question or keyword
     * @param topK     maximum number of results to return (default 5)
     */
    @Tool(description = "Search the RAG knowledge base for information relevant to a query. Returns matching document excerpts with source metadata.")
    public String searchKnowledge(String query, int topK) {
        int k = topK <= 0 ? 5 : Math.min(topK, 20);
        log.debug("[RagMcpService] MCP tool searchKnowledge query='{}' topK={}", query, k);

        // MCP transport bypasses HTTP auth — no user context is available.
        // Pass null for allowedSources so the vector search is unrestricted (same as auth-disabled mode).
        List<DocumentResult> results = retrievalService.retrieve(query, k, Map.of(), null);
        if (results.isEmpty()) {
            return "No relevant documents found for: " + query;
        }

        return results.stream()
                .map(r -> String.format(
                        "Source: %s (score=%.2f)\n%s",
                        r.source(), r.score(), r.content()))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /**
     * Fetch a web page and ingest its content into the Weaviate knowledge base.
     *
     * @param url      the page URL to fetch (http or https)
     * @param category optional category label for metadata filtering
     */
    @Tool(description = "Fetch a URL and add its text content to the RAG knowledge base so it can be queried later.")
    public String ingestUrl(String url, String category) {
        log.info("[RagMcpService] MCP tool ingestUrl url='{}' category='{}'", url, category);
        var result = mcpConnectorService.fetchAndIngest(url, category);
        return String.format(
                "Ingested '%s' from %s — %d chunks added to knowledge base.",
                result.title(), result.url(), result.chunkCount());
    }
}
