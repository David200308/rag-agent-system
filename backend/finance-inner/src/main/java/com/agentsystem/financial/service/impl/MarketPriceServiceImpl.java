package com.agentsystem.financial.service.impl;

import com.agentsystem.financial.service.MarketPriceService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Fetches and caches live market prices.
 *
 * Supported markets: US, HK, CN, JP, FR.
 *
 * Stocks  — Finnhub quote endpoint for US-listed symbols (requires FINNHUB_API_KEY; the free
 *           tier is US-only, which is why every other market below uses a different source).
 *           HK-, CN-, and JP-listed symbols (".HK"/".SS"/".SZ"/".T" suffix) go through Pyth
 *           Hermes, which carries real native-exchange feeds (HKD/CNY/JPY-quoted) for those three
 *           markets and needs no API key — FR-exchange symbols aren't covered by Pyth at all
 *           (verified live — real French tickers like AIR/OR/SIE/BAS return zero matches; a few
 *           coincidentally "match" only via an unrelated US-quoted ADR feed, not a real Euronext
 *           listing).
 *           FR (".PA") symbols instead go through Yahoo Finance's unofficial chart endpoint —
 *           every *official* free tier checked (Finnhub, Twelve Data, FMP, Alpha Vantage, ...)
 *           either excludes Euronext Paris entirely or gates it behind a paid plan; Yahoo's
 *           reverse-engineered `v8/finance/chart` endpoint is the only source confirmed (live,
 *           not just from docs) to actually return real Euronext Paris data, using the exact
 *           same ".PA" suffix already stored — no symbol conversion needed. It needs no API key,
 *           but isn't an official/supported API (Yahoo shut the real one down in 2017): no SLA,
 *           and it 429s without a browser-like User-Agent header (which is why one is set below).
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
public class MarketPriceServiceImpl implements MarketPriceService {

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
    // symbol -> company logo URL (from Finnhub company profile); logos rarely change so
    // once fetched a symbol is never re-fetched (unlike prices, which refresh hourly).
    private final Map<String, String> stockLogos       = new ConcurrentHashMap<>();
    // symbol -> company display name (from Finnhub company profile); cached indefinitely like logos.
    // Powers the Add Stock form's "auto-fill Name from Symbol" lookup.
    private final Map<String, String> stockNames       = new ConcurrentHashMap<>();
    // symbol (HK/CN/JP routed only) -> resolved Pyth Hermes feed ID; cached indefinitely (feed
    // IDs don't change), so we search Pyth's price_feeds endpoint only once per symbol ever.
    private final Map<String, String> pythFeedIds      = new ConcurrentHashMap<>();

    // crypto base symbol (e.g. "BTC", "ETH") -> USDT price
    private final Map<String, Double> cryptoPrices     = new ConcurrentHashMap<>();
    // crypto base symbol -> coin logo URL (from CoinGecko); cached indefinitely like stockLogos.
    private final Map<String, String> cryptoLogos      = new ConcurrentHashMap<>();

    private volatile Instant stockLastFetched  = Instant.EPOCH;
    private volatile Instant cryptoLastFetched = Instant.EPOCH;

    // ── Public accessors ──────────────────────────────────────────────────────

    @Override
    public Optional<Double> getStockPrice(String symbol) {
        return Optional.ofNullable(stockPrices.get(symbol.toUpperCase()));
    }

    @Override
    public Optional<String> getStockCurrency(String symbol) {
        return Optional.ofNullable(stockCurrencies.get(symbol.toUpperCase()));
    }

    @Override
    public Optional<String> getStockLogo(String symbol) {
        return Optional.ofNullable(stockLogos.get(symbol.toUpperCase()));
    }

    @Override
    public Optional<String> lookupStockName(String symbol) {
        String sym = symbol.trim().toUpperCase(Locale.ROOT);
        if (sym.isEmpty()) return Optional.empty();
        String cached = stockNames.get(sym);
        if (cached != null) return Optional.of(cached);
        fetchStockProfile(sym);
        return Optional.ofNullable(stockNames.get(sym));
    }

    @Override
    public Optional<Double> getCryptoPrice(String symbol) {
        return Optional.ofNullable(cryptoPrices.get(symbol.toUpperCase()));
    }

    @Override
    public Optional<String> getCryptoLogo(String symbol) {
        return Optional.ofNullable(cryptoLogos.get(symbol.toUpperCase()));
    }

    @Override
    public Instant getStockLastFetched()  { return stockLastFetched; }
    @Override
    public Instant getCryptoLastFetched() { return cryptoLastFetched; }

    @Override
    public boolean isStockStale()  { return Duration.between(stockLastFetched,  Instant.now()).compareTo(CACHE_TTL) > 0; }
    @Override
    public boolean isCryptoStale() { return Duration.between(cryptoLastFetched, Instant.now()).compareTo(CACHE_TTL) > 0; }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Override
    public synchronized void refreshStockPrices(List<String> symbols) {
        if (symbols.isEmpty()) return;
        List<String> distinct = symbols.stream().map(String::toUpperCase).distinct().collect(Collectors.toList());
        int updated = 0;
        int pythRouted = 0;
        int yahooRouted = 0;
        for (String sym : distinct) {
            boolean ok;
            if (isPythRouted(sym)) {
                pythRouted++;
                ok = fetchStockPriceFromPyth(sym);
            } else if (isYahooRouted(sym)) {
                yahooRouted++;
                ok = fetchStockPriceFromYahoo(sym);
            } else {
                ok = fetchStockPriceFromFinnhub(sym);
            }
            if (ok) updated++;

            if (!stockLogos.containsKey(sym) || !stockNames.containsKey(sym)) {
                fetchStockProfile(sym);
            }
        }
        stockLastFetched = Instant.now();
        log.info("[MarketPriceService] Stock prices refreshed: {}/{} symbols ({} via Pyth, {} via Yahoo, {} via Finnhub)",
                updated, distinct.size(), pythRouted, yahooRouted, distinct.size() - pythRouted - yahooRouted);
    }

    /** HK (".HK"), CN (".SS"/".SZ"), and JP (".T") symbols have real native Pyth feeds; everything else doesn't. */
    private static boolean isPythRouted(String sym) {
        int dot = sym.lastIndexOf('.');
        return dot >= 0 && pythExchangePrefix(sym.substring(dot + 1)) != null;
    }

    /** FR (".PA") is the only market routed to Yahoo Finance's unofficial endpoint. */
    private static boolean isYahooRouted(String sym) {
        return sym.endsWith(".PA");
    }

    /** Maps a Finnhub-format exchange suffix to its Pyth "Equity.{EXCHANGE}." prefix, or null if unsupported. */
    private static String pythExchangePrefix(String suffix) {
        return switch (suffix) {
            case "HK" -> "Equity.HK.";
            case "SS", "SZ" -> "Equity.CN.";
            case "T" -> "Equity.JP.";
            default -> null;
        };
    }

    /**
     * GET https://finnhub.io/api/v1/quote?symbol=AAPL&token=KEY
     * Response: { "c": 261.74, "d": 4.11, "dp": 1.60, "h": 263.31, "l": 259.36, "o": 258.62, "pc": 257.63, "t": 1582641000 }
     */
    private boolean fetchStockPriceFromFinnhub(String sym) {
        try {
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
                return true;
            }
            log.warn("[MarketPriceService] Finnhub returned no price for {} (price=0 or missing)", sym);
            return false;
        } catch (Exception e) {
            log.error("[MarketPriceService] Finnhub stock price fetch failed for {}: {}", sym, e.getMessage());
            return false;
        }
    }

    /**
     * Fetches a France stock's price from Yahoo Finance's unofficial chart endpoint — see the
     * class doc for why this is used instead of an official API (none of the ones checked cover
     * Euronext Paris on a free tier).
     *
     * Endpoint: GET https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=1d&interval=1d
     * Response: { "chart": { "result": [{ "meta": { "regularMarketPrice": 458.05, ... } }] } },
     *           or { "chart": { "result": null, "error": {...} } } for an unknown symbol — both
     *           read through the same price>0 check as a genuine no-data case. Requires a
     *           browser-like User-Agent — Yahoo returns 429 without one.
     */
    private boolean fetchStockPriceFromYahoo(String sym) {
        try {
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/"
                    + URLEncoder.encode(sym, StandardCharsets.UTF_8) + "?range=1d&interval=1d";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode meta = objectMapper.readTree(resp.body())
                    .path("chart").path("result").path(0).path("meta");
            double price = meta.path("regularMarketPrice").asDouble(0);
            if (price > 0) {
                stockPrices.put(sym, price);
                stockCurrencies.put(sym, inferCurrency(sym));
                return true;
            }
            log.warn("[MarketPriceService] Yahoo returned no price for {} (status {})", sym, resp.statusCode());
            return false;
        } catch (Exception e) {
            log.error("[MarketPriceService] Yahoo stock price fetch failed for {}: {}", sym, e.getMessage());
            return false;
        }
    }

    /**
     * Fetches an HK/CN/JP stock's price from Pyth Hermes: resolves (and caches) the symbol's feed
     * ID via a search, then reads its latest price update.
     *
     * Search:  GET https://hermes.pyth.network/v2/price_feeds?query={code}&asset_type=equity
     *          matched by exact exchange-native ticker (attributes.nasdaq_symbol) under the right
     *          "Equity.HK."/"Equity.CN."/"Equity.JP." prefix (attributes.symbol) — Pyth's schema
     *          has no "base" field to match on despite what older snapshots of this API suggested.
     * Price:   GET https://hermes.pyth.network/v2/updates/price/latest?ids[]={feedId}
     *          Response: { "parsed": [{ "price": { "price": "<int string>", "expo": <int>, ... } }] }
     *          Actual price = price * 10^expo.
     */
    private boolean fetchStockPriceFromPyth(String sym) {
        try {
            String feedId = pythFeedIds.computeIfAbsent(sym, this::resolvePythFeedId);
            if (feedId == null) {
                log.warn("[MarketPriceService] No Pyth feed found for {}", sym);
                return false;
            }

            String url = "https://hermes.pyth.network/v2/updates/price/latest?ids[]=" + feedId;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode parsed = objectMapper.readTree(resp.body()).path("parsed");
            if (!parsed.isArray() || parsed.isEmpty()) {
                log.warn("[MarketPriceService] Pyth returned no price update for {} (feed {})", sym, feedId);
                return false;
            }

            JsonNode priceNode = parsed.get(0).path("price");
            long priceInt = priceNode.path("price").asLong(0);
            int expo = priceNode.path("expo").asInt(0);
            double price = priceInt * Math.pow(10, expo);
            if (price > 0) {
                stockPrices.put(sym, price);
                stockCurrencies.put(sym, inferCurrency(sym));
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("[MarketPriceService] Pyth stock price fetch failed for {}: {}", sym, e.getMessage());
            return false;
        }
    }

    /**
     * Resolves an HK/CN/JP Finnhub-format symbol (e.g. "0700.HK", "600519.SS"/"600519.SZ",
     * "7203.T") to its Pyth Hermes feed ID by stripping the suffix and searching Pyth's equity
     * feeds for an exact ticker match under the corresponding exchange prefix. Returns null (not
     * cached, per {@link Map#computeIfAbsent}) if no match is found, so failed lookups retry on
     * next refresh.
     */
    private String resolvePythFeedId(String sym) {
        int dot = sym.lastIndexOf('.');
        if (dot < 0) return null;
        String code = sym.substring(0, dot);
        String exchangePrefix = pythExchangePrefix(sym.substring(dot + 1));
        if (exchangePrefix == null) return null;

        try {
            String url = "https://hermes.pyth.network/v2/price_feeds?query="
                    + URLEncoder.encode(code, StandardCharsets.UTF_8) + "&asset_type=equity";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            return pickNativeExchangeMatch(root, code, exchangePrefix).orElse(null);
        } catch (Exception e) {
            log.warn("[MarketPriceService] Pyth feed resolution failed for {}: {}", sym, e.getMessage());
            return null;
        }
    }

    /**
     * Picks the feed matching an HK/CN/JP exchange-native ticker exactly, under the given
     * "Equity.HK."/"Equity.CN."/"Equity.JP." prefix, skipping deprecated feeds. Extracted from
     * {@link #resolvePythFeedId} so the matching rules can be unit-tested against a fixture
     * without a live HTTP call.
     */
    private static Optional<String> pickNativeExchangeMatch(JsonNode results, String code, String exchangePrefix) {
        if (!results.isArray()) return Optional.empty();
        for (JsonNode node : results) {
            JsonNode attrs = node.path("attributes");
            String symbolField = attrs.path("symbol").asText("");
            String nasdaqSymbol = attrs.path("nasdaq_symbol").asText("");
            String description = attrs.path("description").asText("");
            if (symbolField.startsWith(exchangePrefix)
                    && code.equalsIgnoreCase(nasdaqSymbol)
                    && !description.toUpperCase(Locale.ROOT).contains("DEPRECATED")) {
                return Optional.ofNullable(node.path("id").asText(null));
            }
        }
        return Optional.empty();
    }

    /**
     * Fetches a stock's company display name and logo via Finnhub's company profile endpoint —
     * used for both Finnhub- and Pyth-priced symbols, since Finnhub's profile coverage is
     * independent of where the live price itself comes from.
     *
     * Endpoint: GET https://finnhub.io/api/v1/stock/profile2?symbol=AAPL&token=KEY
     * Response includes "name" and "logo" fields (empty string if unavailable).
     * Cached indefinitely once fetched — neither changes like prices do.
     */
    private void fetchStockProfile(String sym) {
        try {
            String url = "https://finnhub.io/api/v1/stock/profile2?symbol=" + sym + "&token=" + finnhubApiKey;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            String name = root.path("name").asText("");
            if (!name.isBlank()) {
                stockNames.put(sym, name);
            }
            String logo = root.path("logo").asText("");
            if (!logo.isBlank()) {
                stockLogos.put(sym, logo);
            }
        } catch (Exception e) {
            log.warn("[MarketPriceService] Stock profile fetch failed for {}: {}", sym, e.getMessage());
        }
    }

    /** Infer the native currency of a stock symbol from its exchange suffix. */
    private static String inferCurrency(String symbol) {
        String upper = symbol.toUpperCase();
        if (upper.endsWith(".HK"))                          return "HKD";
        if (upper.endsWith(".SS") || upper.endsWith(".SZ")) return "CNY";
        if (upper.endsWith(".T"))                           return "JPY";
        if (upper.endsWith(".AX"))                          return "AUD";
        if (upper.endsWith(".TO"))                          return "CAD";
        if (upper.endsWith(".PA"))                          return "EUR";
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
    @Override
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

        List<String> uncachedLogos = symbols.stream()
                .map(String::toUpperCase).distinct()
                .filter(sym -> !cryptoLogos.containsKey(sym))
                .collect(Collectors.toList());
        if (!uncachedLogos.isEmpty()) {
            fetchCryptoLogos(uncachedLogos);
        }
    }

    /**
     * Fetches coin logos for the given symbols from CoinGecko's public markets endpoint
     * in a single batched request (no API key required for the public tier).
     *
     * Endpoint: GET https://api.coingecko.com/api/v3/coins/markets
     *                ?vs_currency=usd&symbols=btc,eth&per_page=250
     * Response: [{ "symbol": "btc", "image": "https://...png", "market_cap_rank": 1, ... }, ...]
     *
     * Results are ordered by market cap descending by default, and a ticker symbol can be
     * shared by multiple coins — we keep only the first (highest market-cap) match per symbol.
     * Cached indefinitely once fetched — logos don't change like prices do.
     */
    private void fetchCryptoLogos(List<String> symbols) {
        try {
            String symbolParam = symbols.stream()
                    .map(s -> s.toLowerCase(java.util.Locale.ROOT))
                    .collect(Collectors.joining(","));
            String url = "https://api.coingecko.com/api/v3/coins/markets"
                    + "?vs_currency=usd&per_page=250&symbols=" + symbolParam;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            if (!root.isArray()) return;

            for (JsonNode coin : root) {
                String sym = coin.path("symbol").asText("").toUpperCase();
                String image = coin.path("image").asText("");
                if (!sym.isBlank() && !image.isBlank()) {
                    cryptoLogos.putIfAbsent(sym, image);
                }
            }
        } catch (Exception e) {
            log.warn("[MarketPriceService] Crypto logo fetch failed for {}: {}", symbols, e.getMessage());
        }
    }
}
