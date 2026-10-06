package com.agentsystem.webfetch.service;

import java.net.URI;
import java.util.Locale;

public interface WebFetchService {

    int MAX_URL_LENGTH = 2048;

    /**
     * Loads {@code url} in a headless browser (JavaScript executed) and returns its
     * content as Markdown.
     *
     * @throws IllegalArgumentException if the URL is not a well-formed http(s) URL
     * @throws IllegalStateException    if the feature is disabled or the browser is saturated
     * @throws WebFetchException        if the page could not be loaded
     */
    WebPage fetch(String url);

    /** Validates and normalizes a user/LLM-supplied URL. Only absolute http(s) URLs are accepted. */
    static URI parseUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL is empty.");
        }
        String trimmed = url.trim();
        if (trimmed.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("URL is too long.");
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed URL: " + trimmed);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Only http and https URLs can be fetched.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("URL has no host: " + trimmed);
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("URLs with embedded credentials are not allowed.");
        }
        return uri;
    }
}
