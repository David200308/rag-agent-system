package com.agentsystem.mcp.service;

public interface RagMcpService {

    /**
     * Semantic search over the Weaviate knowledge base.
     *
     * @param query    natural-language question or keyword
     * @param topK     maximum number of results to return (default 5)
     */
    String searchKnowledge(String query, int topK);
}
