package com.ragagent.financial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.financial.service.MarketPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
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
}
