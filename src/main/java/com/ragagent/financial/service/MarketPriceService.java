package com.ragagent.financial.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Fetches and caches live market prices.
 *
 * Stocks  — Yahoo Finance    (unofficial v7 quote endpoint; no API key needed)
 * Crypto  — Hyperliquid REST (POST /info {"type":"allMids"}; public, no key needed)
 *           Returns USD mid prices for all perp assets in one request.
 *
 * Prices are shared across all users (they are the same for everyone).
 * Auto-refresh triggers when the cache is older than 1 hour.
 * Manual refresh is available via POST /api/v1/financial/prices/refresh.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPriceService {

    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // symbol (e.g. "AAPL", "0700.HK") -> price in native currency
    private final Map<String, Double> stockPrices      = new ConcurrentHashMap<>();
    // symbol -> currency code returned by Yahoo (USD, HKD, …)
    private final Map<String, String> stockCurrencies  = new ConcurrentHashMap<>();

    // crypto base symbol (e.g. "BTC", "ETH") -> USDT price
    private final Map<String, Double> cryptoPrices     = new ConcurrentHashMap<>();

    private volatile Instant stockLastFetched  = Instant.EPOCH;
    private volatile Instant cryptoLastFetched = Instant.EPOCH;

    // ── Public accessors ──────────────────────────────────────────────────────

    public Optional<Double> getStockPrice(String symbol) {
        return Optional.ofNullable(stockPrices.get(symbol.toUpperCase()));
    }

    public Optional<String> getStockCurrency(String symbol) {
        return Optional.ofNullable(stockCurrencies.get(symbol.toUpperCase()));
    }

    public Optional<Double> getCryptoPrice(String symbol) {
        return Optional.ofNullable(cryptoPrices.get(symbol.toUpperCase()));
    }

    public Instant getStockLastFetched()  { return stockLastFetched; }
    public Instant getCryptoLastFetched() { return cryptoLastFetched; }

    public boolean isStockStale()  { return Duration.between(stockLastFetched,  Instant.now()).compareTo(CACHE_TTL) > 0; }
    public boolean isCryptoStale() { return Duration.between(cryptoLastFetched, Instant.now()).compareTo(CACHE_TTL) > 0; }

    // ── Refresh ───────────────────────────────────────────────────────────────

    public synchronized void refreshStockPrices(List<String> symbols) {
        if (symbols.isEmpty()) return;
        try {
            String joined = symbols.stream()
                    .map(s -> s.toUpperCase())
                    .distinct()
                    .collect(Collectors.joining(","));

            String url = "https://query1.finance.yahoo.com/v7/finance/quote?symbols="
                    + URLEncoder.encode(joined, StandardCharsets.UTF_8)
                    + "&fields=regularMarketPrice,currency";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (compatible)")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode results = objectMapper.readTree(resp.body())
                    .path("quoteResponse").path("result");

            int updated = 0;
            for (JsonNode q : results) {
                String sym   = q.path("symbol").asText("").toUpperCase();
                double price = q.path("regularMarketPrice").asDouble(0);
                String cur   = q.path("currency").asText("USD");
                if (!sym.isEmpty() && price > 0) {
                    stockPrices.put(sym, price);
                    stockCurrencies.put(sym, cur);
                    updated++;
                }
            }
            stockLastFetched = Instant.now();
            log.info("[MarketPriceService] Stock prices refreshed: {} symbols", updated);
        } catch (Exception e) {
            log.error("[MarketPriceService] Stock price fetch failed: {}", e.getMessage());
        }
    }

    /**
     * Fetches all mid prices from Hyperliquid in one POST request, then stores
     * prices for the requested symbols.
     *
     * Endpoint: POST https://api.hyperliquid.xyz/info
     * Body:     {"type":"allMids"}
     * Response: {"BTC":"65000.0","ETH":"3500.0", ...}  (USD mid prices)
     */
    public synchronized void refreshCryptoPrices(List<String> symbols) {
        if (symbols.isEmpty()) return;
        try {
            String body = "{\"type\":\"allMids\"}";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.hyperliquid.xyz/info"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());

            // root is a flat object: { "BTC": "65000.0", "ETH": "3500.0", ... }
            Set<String> wanted = symbols.stream()
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());

            int updated = 0;
            var fields = root.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String sym = entry.getKey().toUpperCase();
                if (wanted.contains(sym)) {
                    double price = entry.getValue().asDouble(0);
                    if (price > 0) {
                        cryptoPrices.put(sym, price);
                        updated++;
                    }
                }
            }
            cryptoLastFetched = Instant.now();
            log.info("[MarketPriceService] Crypto prices refreshed via Hyperliquid: {} symbols", updated);
        } catch (Exception e) {
            log.error("[MarketPriceService] Crypto price fetch failed: {}", e.getMessage());
        }
    }
}
