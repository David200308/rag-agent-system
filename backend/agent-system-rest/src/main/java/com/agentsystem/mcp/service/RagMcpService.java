package com.agentsystem.mcp.service;

public interface RagMcpService {

    /**
     * Semantic search over the Weaviate knowledge base.
     *
     * @param query    natural-language question or keyword
     * @param topK     maximum number of results to return (default 5)
     */
    String searchKnowledge(String query, int topK);

    /**
     * Fetch a web page and ingest its content into the Weaviate knowledge base.
     *
     * @param url      the page URL to fetch (http or https)
     * @param category optional category label for metadata filtering
     */
    String ingestUrl(String url, String category);
}
