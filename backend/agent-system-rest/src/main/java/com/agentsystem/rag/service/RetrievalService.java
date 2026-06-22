package com.agentsystem.rag.service;

import com.agentsystem.schema.DocumentResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RetrievalService {

    /**
     * Retrieve the top-K most similar document chunks for {@code query}.
     *
     * @param query          refined query string from the query-analyser
     * @param topK           maximum number of results
     * @param filters        optional user-supplied metadata filters (e.g. {"category": "ml"})
     * @param allowedSources when non-null, restricts results to chunks whose {@code source}
     *                       metadata is in this set (security boundary). An empty set means
     *                       the caller has no accessible sources — returns nothing.
     */
    List<DocumentResult> retrieve(String query, int topK,
                                   Map<String, String> filters,
                                   Set<String> allowedSources);
}
