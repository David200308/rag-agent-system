package com.ragagent.schema;

/**
 * Result returned after fetching and ingesting a remote URL.
 */
public record UrlIngestionResult(
        String status,
        String url,
        String title,
        int    chunkCount
) {}
