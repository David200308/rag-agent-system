package com.ragagent.financial.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Fetches and caches live market prices.
 *
 * Stocks  — Alpha Vantage GLOBAL_QUOTE endpoint (requires ALPHAVANTAGE_API_KEY)
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

    @Value("${financial.finnhub.api-key:}")
    private String finnhubApiKey;

    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // symbol (e.g. "AAPL", "0700.HK") -> price in native currency
    private final Map<String, Double> stockPrices      = new ConcurrentHashMap<>();
    // symbol -> currency code inferred from exchange suffix (USD, HKD, …)
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
        List<String> distinct = symbols.stream().map(String::toUpperCase).distinct().collect(Collectors.toList());
        int updated = 0;
        for (String sym : distinct) {
            try {
                // GET https://finnhub.io/api/v1/quote?symbol=AAPL&token=KEY
                // Response: { "c": 261.74, "d": 4.11, "dp": 1.60, "h": 263.31, "l": 259.36, "o": 258.62, "pc": 257.63, "t": 1582641000 }
                String url = "https://finnhub.io/api/v1/quote?symbol=" + sym + "&token=" + finnhubApiKey;

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                JsonNode root = objectMapper.readTree(resp.body());
                double price = root.path("c").asDouble(0);
                if (price > 0) {
                    stockPrices.put(sym, price);
                    stockCurrencies.put(sym, inferCurrency(sym));
                    updated++;
                } else {
                    log.warn("[MarketPriceService] Finnhub returned no price for {} (price=0 or missing)", sym);
                }
            } catch (Exception e) {
                log.error("[MarketPriceService] Stock price fetch failed for {}: {}", sym, e.getMessage());
            }
        }
        stockLastFetched = Instant.now();
        log.info("[MarketPriceService] Stock prices refreshed via Finnhub: {}/{} symbols", updated, distinct.size());
    }

    /** Infer the native currency of a stock symbol from its exchange suffix. */
    private static String inferCurrency(String symbol) {
        String upper = symbol.toUpperCase();
        if (upper.endsWith(".HK"))                          return "HKD";
        if (upper.endsWith(".SS") || upper.endsWith(".SZ")) return "CNY";
        if (upper.endsWith(".SI"))                          return "SGD";
        if (upper.endsWith(".T"))                           return "JPY";
        if (upper.endsWith(".L"))                           return "GBP";
        if (upper.endsWith(".AX"))                          return "AUD";
        if (upper.endsWith(".TO"))                          return "CAD";
        return "USD";
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
