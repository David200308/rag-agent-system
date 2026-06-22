package com.agentsystem.rag.service;

import org.springframework.core.io.Resource;

import java.util.Map;

public interface DocumentIngestionService {

    /**
     * Ingest a single file resource with optional metadata.
     *
     * @param replace when true, all existing chunks whose {@code source} metadata matches
     *                the filename (or the explicit "source" value in metadata) are deleted first.
     */
    int ingest(Resource resource, Map<String, Object> metadata, boolean replace);

    /**
     * Ingest plain text directly (e.g. from a web scrape or API response).
     *
     * @param replace when true, existing chunks with the same sourceId are deleted first.
     */
    int ingestText(String text, String sourceId, Map<String, Object> metadata, boolean replace);

    /**
     * Delete all Weaviate chunks whose {@code source} metadata field equals the given value.
     */
    void deleteBySource(String source);
}
