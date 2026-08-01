package com.agentsystem.alert.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Resolves a plain symbol (e.g. "BTC", "QQQ") to a Pyth Hermes price feed ID, so callers
 * (e.g. the Financial section's "Set Alert" button) never need to know or enter a feed ID
 * themselves.
 *
 * Endpoint: GET https://hermes.pyth.network/v2/price_feeds?query={symbol}&asset_type={crypto|equity}
 * A symbol can match multiple feeds (deprecated feeds, pre/post-market variants, leveraged
 * ETFs like TQQQ/SQQQ alongside QQQ) — see {@link #pickBestMatch} for the matching rules.
 *
 * Resolved (symbol, assetType) -> feedId pairs are cached with no TTL (feed IDs don't change),
 * bounded by a max size since {@code symbol} is user-supplied (via the "Set Alert" endpoint) and
 * would otherwise let arbitrary/garbage symbols grow the cache without limit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PythFeedResolver {

    private static final String HERMES_URL = "https://hermes.pyth.network/v2/price_feeds";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Cache<String, String> feedIdCache = Caffeine.newBuilder()
            .maximumSize(2_000)
            .build();

    /**
     * @param symbol    plain symbol, e.g. "BTC" or "QQQ" (case-insensitive)
     * @param assetType "CRYPTO" or "STOCK"
     * @return the resolved Pyth feed ID, or empty if no matching feed was found
     */
    public Optional<String> resolveFeedId(String symbol, String assetType) {
        String base = symbol.trim().toUpperCase(Locale.ROOT);
        String pythAssetType = "STOCK".equalsIgnoreCase(assetType) ? "equity" : "crypto";
        String cacheKey = pythAssetType + ":" + base;

        String cached = feedIdCache.getIfPresent(cacheKey);
        if (cached != null) return Optional.of(cached);

        try {
            String url = HERMES_URL
                    + "?query=" + URLEncoder.encode(base, StandardCharsets.UTF_8)
                    + "&asset_type=" + pythAssetType;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            if (!root.isArray()) return Optional.empty();

            Optional<String> feedId = pickBestMatch(root, base);
            feedId.ifPresent(id -> feedIdCache.put(cacheKey, id));
            return feedId;
        } catch (Exception e) {
            log.warn("[PythFeedResolver] Failed to resolve feed for symbol={} assetType={}: {}", symbol, assetType, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Picks the best-matching feed from Pyth's search results for a given base symbol.
     * Filters to USD-quoted, non-deprecated feeds whose base matches exactly, then prefers
     * the plain regular-hours feed (display_symbol == "{BASE}/USD") over pre/post/overnight
     * variants — falls back to the first remaining candidate otherwise.
     */
    private Optional<String> pickBestMatch(JsonNode results, String base) {
        String plainDisplaySymbol = base + "/USD";

        return StreamSupport.stream(results.spliterator(), false)
                .filter(node -> {
                    JsonNode attrs = node.path("attributes");
                    String candidateBase = attrs.path("base").asText("");
                    String quote = attrs.path("quote_currency").asText("");
                    String description = attrs.path("description").asText("");
                    return candidateBase.equalsIgnoreCase(base)
                            && "USD".equalsIgnoreCase(quote)
                            && !description.toUpperCase(Locale.ROOT).contains("DEPRECATED");
                })
                .max(Comparator.comparing(node ->
                        plainDisplaySymbol.equalsIgnoreCase(node.path("attributes").path("display_symbol").asText(""))))
                .map(node -> node.path("id").asText(null));
    }
}
