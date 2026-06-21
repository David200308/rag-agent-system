package com.agentsystem.financial.service;

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
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private static final String RATES_URL = "https://open.er-api.com/v6/latest/USD";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final ObjectMapper objectMapper;
    private final HttpClient   httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private volatile Map<String, Double> cachedRates = new HashMap<>();
    private volatile Instant lastFetched = Instant.EPOCH;

    public synchronized Map<String, Double> getRates() {
        if (cachedRates.isEmpty() || Duration.between(lastFetched, Instant.now()).compareTo(CACHE_TTL) > 0) {
            fetchRates();
        }
        return cachedRates;
    }

    /** Convert {@code amount} from one currency to another through USD as the base. */
    public double convert(double amount, String from, String to) {
        if (from == null || to == null || from.equalsIgnoreCase(to)) return amount;
        Map<String, Double> rates = getRates();
        double fromRate = rates.getOrDefault(from.toUpperCase(), 1.0);
        double toRate   = rates.getOrDefault(to.toUpperCase(),   1.0);
        if (fromRate == 0) return amount;
        return (amount / fromRate) * toRate;
    }

    public Instant getLastFetched() { return lastFetched; }

    private void fetchRates() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(RATES_URL))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            // open.er-api.com (free) uses "rates"; paid exchangerate-api.com uses "conversion_rates"
            JsonNode cr = root.has("rates") ? root.path("rates") : root.path("conversion_rates");
            Map<String, Double> rates = new HashMap<>();
            cr.fields().forEachRemaining(e -> rates.put(e.getKey(), e.getValue().asDouble()));
            if (!rates.isEmpty()) {
                cachedRates = rates;
                lastFetched = Instant.now();
                log.info("[ExchangeRateService] Fetched {} currency rates", rates.size());
            }
        } catch (Exception e) {
            log.error("[ExchangeRateService] Rate fetch failed: {}", e.getMessage());
        }
    }
}
