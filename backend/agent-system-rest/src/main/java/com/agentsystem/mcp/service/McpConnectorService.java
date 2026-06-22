package com.agentsystem.mcp.service;

import com.agentsystem.schema.UrlIngestionResult;

public interface McpConnectorService {

    /**
     * Fetch {@code url}, strip HTML, and ingest the resulting text into Weaviate.
     *
     * @param url      the page to fetch (http/https)
     * @param category optional metadata category tag (may be null)
     */
    UrlIngestionResult fetchAndIngest(String url, String category);

    UrlIngestionResult fetchAndIngest(String url, String category, String ownerEmail);
}
