package com.agentsystem.financial.service;

import com.agentsystem.financial.service.impl.MarketPriceServiceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class MarketPriceServiceTest {

    MarketPriceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MarketPriceServiceImpl(new ObjectMapper());
    }

    // ── Staleness ─────────────────────────────────────────────────────────────

    @Test
    void isStockStale_initialState_isTrue() {
        assertThat(service.isStockStale()).isTrue();
    }

    @Test
    void isCryptoStale_initialState_isTrue() {
        assertThat(service.isCryptoStale()).isTrue();
    }

    @Test
    void isStockStale_afterRecentFetch_isFalse() {
        ReflectionTestUtils.setField(service, "stockLastFetched", Instant.now());
        assertThat(service.isStockStale()).isFalse();
    }

    @Test
    void isCryptoStale_afterRecentFetch_isFalse() {
        ReflectionTestUtils.setField(service, "cryptoLastFetched", Instant.now());
        assertThat(service.isCryptoStale()).isFalse();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    @Test
    void getStockPrice_notCached_returnsEmpty() {
        assertThat(service.getStockPrice("AAPL")).isEmpty();
    }

    @Test
    void getCryptoPrice_notCached_returnsEmpty() {
        assertThat(service.getCryptoPrice("BTC")).isEmpty();
    }

    @Test
    void getStockCurrency_notCached_returnsEmpty() {
        assertThat(service.getStockCurrency("AAPL")).isEmpty();
    }

    @Test
    void getStockLogo_notCached_returnsEmpty() {
        assertThat(service.getStockLogo("AAPL")).isEmpty();
    }

    @Test
    void getCryptoLogo_notCached_returnsEmpty() {
        assertThat(service.getCryptoLogo("BTC")).isEmpty();
    }

    @Test
    void getStockPrice_seeded_returnsValue() {
        Map<String, Double> prices = new ConcurrentHashMap<>(Map.of("AAPL", 195.0, "TSLA", 250.0));
        ReflectionTestUtils.setField(service, "stockPrices", prices);

        assertThat(service.getStockPrice("AAPL")).hasValue(195.0);
        assertThat(service.getStockPrice("TSLA")).hasValue(250.0);
        assertThat(service.getStockPrice("MSFT")).isEmpty();
    }

    @Test
    void getCryptoPrice_seeded_returnsValue() {
        Map<String, Double> prices = new ConcurrentHashMap<>(Map.of("BTC", 65000.0, "ETH", 3500.0));
        ReflectionTestUtils.setField(service, "cryptoPrices", prices);

        assertThat(service.getCryptoPrice("BTC")).hasValue(65000.0);
        assertThat(service.getCryptoPrice("SOL")).isEmpty();
    }

    // ── Currency inference ────────────────────────────────────────────────────

    @Test
    void getStockCurrency_seeded_returnsCorrectCurrencyPerExchange() {
        Map<String, String> currencies = new ConcurrentHashMap<>(Map.of(
                "0700.HK", "HKD",
                "600036.SS", "CNY",
                "7203.T", "JPY",
                "BHP.AX", "AUD",
                "RY.TO", "CAD",
                "AAPL", "USD"
        ));
        ReflectionTestUtils.setField(service, "stockCurrencies", currencies);

        assertThat(service.getStockCurrency("0700.HK")).hasValue("HKD");
        assertThat(service.getStockCurrency("600036.SS")).hasValue("CNY");
        assertThat(service.getStockCurrency("7203.T")).hasValue("JPY");
        assertThat(service.getStockCurrency("BHP.AX")).hasValue("AUD");
        assertThat(service.getStockCurrency("RY.TO")).hasValue("CAD");
        assertThat(service.getStockCurrency("AAPL")).hasValue("USD");
    }

    @Test
    void lookupStockName_cached_returnsWithoutFetching() {
        Map<String, String> names = new ConcurrentHashMap<>(Map.of("AAPL", "Apple Inc."));
        ReflectionTestUtils.setField(service, "stockNames", names);

        assertThat(service.lookupStockName("aapl")).hasValue("Apple Inc.");
    }

    @Test
    void lookupStockName_blankSymbol_returnsEmptyWithoutFetching() {
        assertThat(service.lookupStockName("  ")).isEmpty();
    }

    // ── Logo cache ────────────────────────────────────────────────────────────

    @Test
    void getStockLogo_seeded_returnsValue() {
        Map<String, String> logos = new ConcurrentHashMap<>(Map.of(
                "AAPL", "https://static.finnhub.io/logo/aapl.png"
        ));
        ReflectionTestUtils.setField(service, "stockLogos", logos);

        assertThat(service.getStockLogo("AAPL")).hasValue("https://static.finnhub.io/logo/aapl.png");
        assertThat(service.getStockLogo("MSFT")).isEmpty();
    }

    @Test
    void getStockLogo_caseInsensitive_returnsValue() {
        Map<String, String> logos = new ConcurrentHashMap<>(Map.of(
                "AAPL", "https://static.finnhub.io/logo/aapl.png"
        ));
        ReflectionTestUtils.setField(service, "stockLogos", logos);

        assertThat(service.getStockLogo("aapl")).hasValue("https://static.finnhub.io/logo/aapl.png");
        assertThat(service.getStockLogo("Aapl")).hasValue("https://static.finnhub.io/logo/aapl.png");
    }

    @Test
    void getCryptoLogo_seeded_returnsValue() {
        Map<String, String> logos = new ConcurrentHashMap<>(Map.of(
                "BTC", "https://coin-images.coingecko.com/coins/images/1/large/bitcoin.png"
        ));
        ReflectionTestUtils.setField(service, "cryptoLogos", logos);

        assertThat(service.getCryptoLogo("BTC")).hasValue("https://coin-images.coingecko.com/coins/images/1/large/bitcoin.png");
        assertThat(service.getCryptoLogo("ETH")).isEmpty();
    }

    @Test
    void getCryptoLogo_caseInsensitive_returnsValue() {
        Map<String, String> logos = new ConcurrentHashMap<>(Map.of(
                "BTC", "https://coin-images.coingecko.com/coins/images/1/large/bitcoin.png"
        ));
        ReflectionTestUtils.setField(service, "cryptoLogos", logos);

        assertThat(service.getCryptoLogo("btc")).hasValue("https://coin-images.coingecko.com/coins/images/1/large/bitcoin.png");
        assertThat(service.getCryptoLogo("Btc")).hasValue("https://coin-images.coingecko.com/coins/images/1/large/bitcoin.png");
    }

    // ── Timestamps ───────────────────────────────────────────────────────────

    @Test
    void getStockLastFetched_initialState_isEpoch() {
        assertThat(service.getStockLastFetched()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void getCryptoLastFetched_initialState_isEpoch() {
        assertThat(service.getCryptoLastFetched()).isEqualTo(Instant.EPOCH);
    }

    // ── Currency inference via reflection ─────────────────────────────────────

    @Test
    void inferCurrency_hkSuffix_returnsHKD() throws Exception {
        assertThat(callInferCurrency("0700.HK")).isEqualTo("HKD");
    }

    @Test
    void inferCurrency_ssSuffix_returnsCNY() throws Exception {
        assertThat(callInferCurrency("600036.SS")).isEqualTo("CNY");
    }

    @Test
    void inferCurrency_szSuffix_returnsCNY() throws Exception {
        assertThat(callInferCurrency("000001.SZ")).isEqualTo("CNY");
    }

    @Test
    void inferCurrency_tSuffix_returnsJPY() throws Exception {
        assertThat(callInferCurrency("7203.T")).isEqualTo("JPY");
    }

    @Test
    void inferCurrency_axSuffix_returnsAUD() throws Exception {
        assertThat(callInferCurrency("BHP.AX")).isEqualTo("AUD");
    }

    @Test
    void inferCurrency_toSuffix_returnsCAD() throws Exception {
        assertThat(callInferCurrency("RY.TO")).isEqualTo("CAD");
    }

    @Test
    void inferCurrency_paSuffix_returnsEUR() throws Exception {
        assertThat(callInferCurrency("MC.PA")).isEqualTo("EUR");
    }

    @Test
    void inferCurrency_noSuffix_returnsUSD() throws Exception {
        assertThat(callInferCurrency("AAPL")).isEqualTo("USD");
        assertThat(callInferCurrency("TSLA")).isEqualTo("USD");
        assertThat(callInferCurrency("MSFT")).isEqualTo("USD");
    }

    @Test
    void inferCurrency_lowercase_isUppercasedBeforeMatch() throws Exception {
        assertThat(callInferCurrency("0700.hk")).isEqualTo("HKD");
    }

    // ── getStockPrice — case-insensitive lookup ───────────────────────────────

    @Test
    void getStockPrice_caseInsensitive_returnsValue() {
        Map<String, Double> prices = new ConcurrentHashMap<>(Map.of("AAPL", 195.0));
        ReflectionTestUtils.setField(service, "stockPrices", prices);

        assertThat(service.getStockPrice("aapl")).hasValue(195.0);
        assertThat(service.getStockPrice("Aapl")).hasValue(195.0);
    }

    @Test
    void getCryptoPrice_caseInsensitive_returnsValue() {
        Map<String, Double> prices = new ConcurrentHashMap<>(Map.of("ETH", 3500.0));
        ReflectionTestUtils.setField(service, "cryptoPrices", prices);

        assertThat(service.getCryptoPrice("eth")).hasValue(3500.0);
        assertThat(service.getCryptoPrice("Eth")).hasValue(3500.0);
    }

    // ── refreshStockPrices/refreshCryptoPrices — empty list ───────────────────

    @Test
    void refreshStockPrices_emptyList_doesNothing() {
        service.refreshStockPrices(List.of());
        // No HTTP call should happen; stock prices remain empty
        assertThat(service.getStockPrice("AAPL")).isEmpty();
    }

    @Test
    void refreshCryptoPrices_emptyList_doesNothing() {
        service.refreshCryptoPrices(List.of());
        assertThat(service.getCryptoPrice("BTC")).isEmpty();
    }

    // ── Pyth routing (HK/CN/JP only) ────────────────────────────────────────────

    @Test
    void isPythRouted_hkSuffix_isTrue() throws Exception {
        assertThat(callIsPythRouted("0700.HK")).isTrue();
    }

    @Test
    void isPythRouted_ssAndSzSuffix_isTrue() throws Exception {
        assertThat(callIsPythRouted("600519.SS")).isTrue();
        assertThat(callIsPythRouted("000001.SZ")).isTrue();
    }

    @Test
    void isPythRouted_tSuffix_isTrue() throws Exception {
        assertThat(callIsPythRouted("7203.T")).isTrue();
    }

    @Test
    void isPythRouted_usFr_isFalse() throws Exception {
        assertThat(callIsPythRouted("AAPL")).isFalse();
        assertThat(callIsPythRouted("MC.PA")).isFalse();
    }

    // ── Yahoo routing (FR only) ──────────────────────────────────────────────────

    @Test
    void isYahooRouted_paSuffix_isTrue() throws Exception {
        assertThat(callIsYahooRouted("MC.PA")).isTrue();
    }

    @Test
    void isYahooRouted_usHkJp_isFalse() throws Exception {
        assertThat(callIsYahooRouted("AAPL")).isFalse();
        assertThat(callIsYahooRouted("0700.HK")).isFalse();
        assertThat(callIsYahooRouted("7203.T")).isFalse();
    }

    // ── Pyth HK/CN/JP feed matching ─────────────────────────────────────────────

    private static final String TENCENT_FIXTURE = """
            [
              {
                "id": "2229ed6410e4f9e0a91b74a2f08c3048cfb6c2c80b3f1a4dbbfb8765b653cef1",
                "attributes": {"asset_type":"Equity","description":"TENCENT HOLDINGS LTD / HONG KONG DOLLAR","display_symbol":"TENCENT","nasdaq_symbol":"0700","quote_currency":"HKD","symbol":"Equity.HK.0700/HKD"}
              }
            ]
            """;

    private static final String TOYOTA_FIXTURE = """
            [
              {
                "id": "5679f0e0c3934e6f21d5c2e8f0a1f3b0d5c6e7f8a9b0c1d2e3f4a5b6c7d8e9f0",
                "attributes": {"asset_type":"Equity","description":"TOYOTA MOTOR CORPORATION / JAPANESE YEN","display_symbol":"TOYOTA","nasdaq_symbol":"7203","quote_currency":"JPY","symbol":"Equity.JP.7203/JPY"}
              }
            ]
            """;

    @Test
    void pickNativeExchangeMatch_exactTickerUnderExchangePrefix_returnsId() throws Exception {
        JsonNode results = new ObjectMapper().readTree(TENCENT_FIXTURE);

        Optional<String> id = callPickNativeExchangeMatch(results, "0700", "Equity.HK.");

        assertThat(id).contains("2229ed6410e4f9e0a91b74a2f08c3048cfb6c2c80b3f1a4dbbfb8765b653cef1");
    }

    @Test
    void pickNativeExchangeMatch_jpTickerUnderJpPrefix_returnsId() throws Exception {
        JsonNode results = new ObjectMapper().readTree(TOYOTA_FIXTURE);

        Optional<String> id = callPickNativeExchangeMatch(results, "7203", "Equity.JP.");

        assertThat(id).contains("5679f0e0c3934e6f21d5c2e8f0a1f3b0d5c6e7f8a9b0c1d2e3f4a5b6c7d8e9f0");
    }

    @Test
    void pickNativeExchangeMatch_wrongExchangePrefix_returnsEmpty() throws Exception {
        JsonNode results = new ObjectMapper().readTree(TENCENT_FIXTURE);

        Optional<String> id = callPickNativeExchangeMatch(results, "0700", "Equity.CN.");

        assertThat(id).isEmpty();
    }

    @Test
    void pickNativeExchangeMatch_noTickerMatch_returnsEmpty() throws Exception {
        JsonNode results = new ObjectMapper().readTree(TENCENT_FIXTURE);

        Optional<String> id = callPickNativeExchangeMatch(results, "9988", "Equity.HK.");

        assertThat(id).isEmpty();
    }

    @Test
    void pickNativeExchangeMatch_deprecatedFeed_isExcluded() throws Exception {
        String fixture = """
                [
                  {"id":"dep-1","attributes":{"description":"DEPRECATED FEED - TENCENT / HONG KONG DOLLAR","nasdaq_symbol":"0700","symbol":"Equity.HK.0700/HKD"}}
                ]
                """;
        JsonNode results = new ObjectMapper().readTree(fixture);

        Optional<String> id = callPickNativeExchangeMatch(results, "0700", "Equity.HK.");

        assertThat(id).isEmpty();
    }

    // ── reflection helper ─────────────────────────────────────────────────────

    private String callInferCurrency(String symbol) throws Exception {
        java.lang.reflect.Method m = MarketPriceServiceImpl.class.getDeclaredMethod(
                "inferCurrency", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, symbol);  // static method
    }

    private boolean callIsPythRouted(String symbol) throws Exception {
        java.lang.reflect.Method m = MarketPriceServiceImpl.class.getDeclaredMethod(
                "isPythRouted", String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, symbol);  // static method
    }

    private boolean callIsYahooRouted(String symbol) throws Exception {
        java.lang.reflect.Method m = MarketPriceServiceImpl.class.getDeclaredMethod(
                "isYahooRouted", String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, symbol);  // static method
    }

    @SuppressWarnings("unchecked")
    private Optional<String> callPickNativeExchangeMatch(JsonNode results, String code, String exchangePrefix) throws Exception {
        java.lang.reflect.Method m = MarketPriceServiceImpl.class.getDeclaredMethod(
                "pickNativeExchangeMatch", JsonNode.class, String.class, String.class);
        m.setAccessible(true);
        return (Optional<String>) m.invoke(null, results, code, exchangePrefix);  // static method
    }
}
