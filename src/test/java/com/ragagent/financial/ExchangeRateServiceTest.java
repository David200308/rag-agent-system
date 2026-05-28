package com.ragagent.financial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.financial.service.ExchangeRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ExchangeRateServiceTest {

    ExchangeRateService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeRateService(new ObjectMapper());
        // Seed cache directly so tests don't make real HTTP calls.
        // USD is the base (rate = 1.0); all others are relative to USD.
        Map<String, Double> rates = Map.of(
                "USD", 1.0,
                "HKD", 7.8,
                "EUR", 0.92,
                "JPY", 155.0,
                "CNY", 7.25
        );
        ReflectionTestUtils.setField(service, "cachedRates", rates);
        ReflectionTestUtils.setField(service, "lastFetched", Instant.now());
    }

    @Test
    void convert_sameCurrency_returnsUnchanged() {
        assertThat(service.convert(1000.0, "USD", "USD")).isEqualTo(1000.0);
    }

    @Test
    void convert_nullFrom_returnsUnchanged() {
        assertThat(service.convert(500.0, null, "HKD")).isEqualTo(500.0);
    }

    @Test
    void convert_nullTo_returnsUnchanged() {
        assertThat(service.convert(500.0, "USD", null)).isEqualTo(500.0);
    }

    @Test
    void convert_usdToHkd_appliesRate() {
        double result = service.convert(100.0, "USD", "HKD");
        assertThat(result).isCloseTo(780.0, within(0.01));
    }

    @Test
    void convert_hkdToUsd_invertsRate() {
        double result = service.convert(780.0, "HKD", "USD");
        assertThat(result).isCloseTo(100.0, within(0.01));
    }

    @Test
    void convert_crossRate_hkdToEur() {
        // 780 HKD → USD → EUR  =  780/7.8 * 0.92 = 92 EUR
        double result = service.convert(780.0, "HKD", "EUR");
        assertThat(result).isCloseTo(92.0, within(0.01));
    }

    @Test
    void convert_caseInsensitive() {
        double lower = service.convert(100.0, "usd", "hkd");
        double upper = service.convert(100.0, "USD", "HKD");
        assertThat(lower).isEqualTo(upper);
    }

    @Test
    void convert_unknownCurrency_fallsBackToOneRate() {
        // Unknown currency defaults to 1.0, so 100 USD -> 100 UNKNOWN
        double result = service.convert(100.0, "USD", "XYZ");
        assertThat(result).isCloseTo(100.0, within(0.01));
    }

    @Test
    void getLastFetched_afterSeeding_isRecent() {
        assertThat(service.getLastFetched()).isAfter(Instant.now().minusSeconds(5));
    }
}
