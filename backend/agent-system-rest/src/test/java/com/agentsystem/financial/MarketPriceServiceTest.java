package com.ragagent.financial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.financial.service.MarketPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class MarketPriceServiceTest {

    MarketPriceService service;

    @BeforeEach
    void setUp() {
        service = new MarketPriceService(new ObjectMapper());
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
                "D05.SI", "SGD",
                "7203.T", "JPY",
                "BP.L", "GBP",
                "BHP.AX", "AUD",
                "RY.TO", "CAD",
                "AAPL", "USD"
        ));
        ReflectionTestUtils.setField(service, "stockCurrencies", currencies);

        assertThat(service.getStockCurrency("0700.HK")).hasValue("HKD");
        assertThat(service.getStockCurrency("600036.SS")).hasValue("CNY");
        assertThat(service.getStockCurrency("D05.SI")).hasValue("SGD");
        assertThat(service.getStockCurrency("7203.T")).hasValue("JPY");
        assertThat(service.getStockCurrency("BP.L")).hasValue("GBP");
        assertThat(service.getStockCurrency("BHP.AX")).hasValue("AUD");
        assertThat(service.getStockCurrency("RY.TO")).hasValue("CAD");
        assertThat(service.getStockCurrency("AAPL")).hasValue("USD");
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
    void inferCurrency_siSuffix_returnsSGD() throws Exception {
        assertThat(callInferCurrency("D05.SI")).isEqualTo("SGD");
    }

    @Test
    void inferCurrency_tSuffix_returnsJPY() throws Exception {
        assertThat(callInferCurrency("7203.T")).isEqualTo("JPY");
    }

    @Test
    void inferCurrency_lSuffix_returnsGBP() throws Exception {
        assertThat(callInferCurrency("BP.L")).isEqualTo("GBP");
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

    // ── reflection helper ─────────────────────────────────────────────────────

    private String callInferCurrency(String symbol) throws Exception {
        java.lang.reflect.Method m = MarketPriceService.class.getDeclaredMethod(
                "inferCurrency", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, symbol);  // static method
    }
}
