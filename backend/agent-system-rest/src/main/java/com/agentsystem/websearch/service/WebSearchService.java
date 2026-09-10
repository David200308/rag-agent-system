package com.agentsystem.websearch.service;

import java.util.List;

public interface WebSearchService {

    /**
     * Runs a real, open-ended web search (as opposed to fetching a specific known URL —
     * see {@code WebFetchService}) and returns the top organic results.
     *
     * @param query      the search query
     * @param maxResults upper bound on results to return; capped by the configured
     *                   {@code web-search.max-results}
     * @throws IllegalStateException if web search is disabled
     */
    List<WebSearchResult> search(String query, int maxResults);
}
