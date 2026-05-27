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

/**
 * Fetches and caches live market prices.
 *
 * Stocks  — Yahoo Finance  (unofficial v7 quote endpoint; no API key needed)
 * Crypto  — Binance REST   (/api/v3/ticker/price; public, no key needed)
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

    public synchronized void refreshCryptoPrices(List<String> symbols) {
        if (symbols.isEmpty()) return;
        try {
            // BTC -> BTCUSDT, ETH -> ETHUSDT, etc.
            List<String> pairs = symbols.stream()
                    .map(s -> s.toUpperCase() + "USDT")
                    .distinct()
                    .collect(Collectors.toList());

            String symbolsJson = pairs.stream()
                    .map(s -> "\"" + s + "\"")
                    .collect(Collectors.joining(",", "[", "]"));

            String url = "https://api.binance.com/api/v3/ticker/price?symbols="
                    + URLEncoder.encode(symbolsJson, StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());

            int updated = 0;
            if (root.isArray()) {
                for (JsonNode ticker : root) {
                    String pair  = ticker.path("symbol").asText("");
                    double price = ticker.path("price").asDouble(0);
                    if (pair.endsWith("USDT") && price > 0) {
                        String base = pair.substring(0, pair.length() - 4);
                        cryptoPrices.put(base, price);
                        updated++;
                    }
                }
            }
            cryptoLastFetched = Instant.now();
            log.info("[MarketPriceService] Crypto prices refreshed: {} symbols", updated);
        } catch (Exception e) {
            log.error("[MarketPriceService] Crypto price fetch failed: {}", e.getMessage());
        }
    }
}
