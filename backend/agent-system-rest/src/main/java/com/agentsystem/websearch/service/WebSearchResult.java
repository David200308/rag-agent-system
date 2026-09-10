package com.agentsystem.websearch.service;

/** One organic result returned by {@link WebSearchService#search}. */
public record WebSearchResult(String title, String url, String snippet) {}
