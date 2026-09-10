package com.agentsystem.websearch.service.impl;

import com.agentsystem.config.WebSearchProperties;
import com.agentsystem.websearch.service.WebSearchResult;
import com.agentsystem.websearch.service.WebSearchService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Calls a self-hosted SearXNG instance's JSON API (see docker-compose.yml's
 * {@code searxng} service and searxng/settings.yml) — free, no API key, no query cap,
 * aggregating results from Google/Bing/DuckDuckGo/etc.
 */
@Slf4j
@Service
public class WebSearchServiceImpl implements WebSearchService {

    private final RestClient          restClient;
    private final WebSearchProperties props;

    public WebSearchServiceImpl(WebSearchProperties props) {
        this.props = props;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.timeoutSeconds() * 1000);
        factory.setReadTimeout(props.timeoutSeconds() * 1000);

        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) {
        if (!props.enabled()) {
            throw new IllegalStateException("Web search is disabled.");
        }

        log.info("[WebSearchService] Searching: '{}'", query);
        SearxngResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("q", query)
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .body(SearxngResponse.class);

        if (response == null || response.results() == null || response.results().isEmpty()) {
            log.info("[WebSearchService] No results for '{}'", query);
            return List.of();
        }

        int limit = Math.max(1, Math.min(maxResults, props.maxResults()));
        List<WebSearchResult> results = response.results().stream()
                .limit(limit)
                .map(r -> new WebSearchResult(
                        r.title() != null && !r.title().isBlank() ? r.title() : "(untitled)",
                        r.url(),
                        r.content() != null ? r.content() : ""))
                .toList();

        log.info("[WebSearchService] {} result(s) for '{}'", results.size(), query);
        return results;
    }

    // ── SearXNG JSON API wire shapes (only the fields we use) ──────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearxngResponse(List<SearxngResult> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearxngResult(String title, String url, String content) {}
}
