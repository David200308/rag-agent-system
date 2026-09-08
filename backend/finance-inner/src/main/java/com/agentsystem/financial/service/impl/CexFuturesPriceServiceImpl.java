package com.agentsystem.financial.service.impl;

import com.agentsystem.financial.service.CexFuturesPriceService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Fetches live futures/perp mark prices directly from each exchange's own public API —
 * Binance and OKX support per-symbol quotes with no auth key required; Kraken Futures
 * only exposes a bulk "all tickers" endpoint, so it's fetched once and filtered.
 *
 * Cache key is "EXCHANGE:SYMBOL" — each exchange has its own native instrument id
 * (Binance "BTCUSDT", OKX "BTC-USDT-SWAP", Kraken "PF_XBTUSD") and these must never be
 * conflated across exchanges, or with the separate Hyperliquid spot-mids cache in
 * {@link MarketPriceServiceImpl} which uses bare base symbols like "BTC".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CexFuturesPriceServiceImpl implements CexFuturesPriceService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Map<String, Double> prices = new ConcurrentHashMap<>();
    private volatile Instant lastFetched = Instant.EPOCH;

    @Override
    public Optional<Double> getPrice(String exchange, String symbol) {
        return Optional.ofNullable(prices.get(key(exchange, symbol)));
    }

    @Override
    public boolean isStale() {
        return Duration.between(lastFetched, Instant.now()).compareTo(CACHE_TTL) > 0;
    }

    @Override
    public synchronized void refreshPrices(List<ExchangeSymbol> symbols) {
        if (symbols.isEmpty()) return;

        List<String> binance = distinctSymbols(symbols, "BINANCE");
        List<String> okx     = distinctSymbols(symbols, "OKX");
        Set<String>  kraken  = new HashSet<>(distinctSymbols(symbols, "KRAKEN"));

        binance.forEach(this::fetchBinance);
        okx.forEach(this::fetchOkx);
        if (!kraken.isEmpty()) fetchKrakenAll(kraken);

        lastFetched = Instant.now();
    }

    private List<String> distinctSymbols(List<ExchangeSymbol> symbols, String exchange) {
        return symbols.stream()
                .filter(s -> exchange.equals(s.exchange()))
                .map(s -> s.symbol().toUpperCase())
                .distinct().collect(Collectors.toList());
    }

    /** GET https://fapi.binance.com/fapi/v1/ticker/price?symbol=BTCUSDT -> {"symbol":"BTCUSDT","price":"65000.10"} */
    private void fetchBinance(String symbol) {
        try {
            JsonNode root = get("https://fapi.binance.com/fapi/v1/ticker/price?symbol=" + symbol);
            double price = root.path("price").asDouble(0);
            if (price > 0) prices.put(key("BINANCE", symbol), price);
            else log.warn("[CexFuturesPriceService] Binance returned no price for {}", symbol);
        } catch (Exception e) {
            log.error("[CexFuturesPriceService] Binance price fetch failed for {}: {}", symbol, e.getMessage());
        }
    }

    /** GET https://www.okx.com/api/v5/market/ticker?instId=BTC-USDT-SWAP -> {"data":[{"last":"65000.1"}]} */
    private void fetchOkx(String symbol) {
        try {
            JsonNode root = get("https://www.okx.com/api/v5/market/ticker?instId=" + symbol);
            JsonNode data = root.path("data");
            if (data.isArray() && data.size() > 0) {
                double price = data.get(0).path("last").asDouble(0);
                if (price > 0) prices.put(key("OKX", symbol), price);
            } else {
                log.warn("[CexFuturesPriceService] OKX returned no data for {}", symbol);
            }
        } catch (Exception e) {
            log.error("[CexFuturesPriceService] OKX price fetch failed for {}: {}", symbol, e.getMessage());
        }
    }

    /** GET https://futures.kraken.com/derivatives/api/v3/tickers -> {"tickers":[{"symbol":"PF_XBTUSD","markPrice":...,"last":...}]} */
    private void fetchKrakenAll(Set<String> wanted) {
        try {
            JsonNode root = get("https://futures.kraken.com/derivatives/api/v3/tickers");
            JsonNode tickers = root.path("tickers");
            if (!tickers.isArray()) return;

            int updated = 0;
            for (JsonNode t : tickers) {
                String sym = t.path("symbol").asText("").toUpperCase();
                if (!wanted.contains(sym)) continue;
                double price = t.path("markPrice").asDouble(t.path("last").asDouble(0));
                if (price > 0) {
                    prices.put(key("KRAKEN", sym), price);
                    updated++;
                }
            }
            log.info("[CexFuturesPriceService] Kraken prices refreshed: {}/{} symbols", updated, wanted.size());
        } catch (Exception e) {
            log.error("[CexFuturesPriceService] Kraken price fetch failed: {}", e.getMessage());
        }
    }

    private JsonNode get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }

    private static String key(String exchange, String symbol) {
        return exchange.toUpperCase() + ":" + symbol.toUpperCase();
    }
}
