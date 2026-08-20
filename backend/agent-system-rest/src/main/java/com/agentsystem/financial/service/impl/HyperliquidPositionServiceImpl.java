package com.agentsystem.financial.service.impl;

import com.agentsystem.financial.service.HyperliquidPositionService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches open perp positions for a Hyperliquid wallet address via the public
 * clearinghouseState endpoint (no API key required).
 *
 * Endpoint: POST https://api.hyperliquid.xyz/info
 * Body:     {"type":"clearinghouseState","user":"<address>"}
 *
 * Results are cached per-address for a short TTL so repeatedly listing a user's
 * futures within the same session doesn't hammer the API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HyperliquidPositionServiceImpl implements HyperliquidPositionService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private record CacheEntry(List<Position> positions, Instant fetchedAt) {}

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public List<Position> fetchPositions(String address) {
        if (address == null || address.isBlank()) return List.of();
        String key = address.toLowerCase(Locale.ROOT);

        CacheEntry cached = cache.get(key);
        if (cached != null && Duration.between(cached.fetchedAt(), Instant.now()).compareTo(CACHE_TTL) <= 0) {
            return cached.positions();
        }

        List<Position> positions = fetchFromApi(address);
        cache.put(key, new CacheEntry(positions, Instant.now()));
        return positions;
    }

    private List<Position> fetchFromApi(String address) {
        List<Position> positions = new ArrayList<>();
        try {
            String body = "{\"type\":\"clearinghouseState\",\"user\":\"" + address + "\"}";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.hyperliquid.xyz/info"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode assetPositions = root.path("assetPositions");
            if (!assetPositions.isArray()) return positions;

            for (JsonNode entry : assetPositions) {
                JsonNode pos = entry.path("position");
                if (pos.isMissingNode()) continue;

                String coin = pos.path("coin").asText(null);
                BigDecimal szi = toBigDecimal(pos.path("szi").asText(null));
                if (coin == null || szi == null || szi.signum() == 0) continue;

                BigDecimal entryPx = toBigDecimal(pos.path("entryPx").asText(null));
                BigDecimal leverage = toBigDecimal(pos.path("leverage").path("value").asText(null));
                BigDecimal unrealizedPnl = toBigDecimal(pos.path("unrealizedPnl").asText(null));
                BigDecimal positionValue = toBigDecimal(pos.path("positionValue").asText(null));

                BigDecimal size = szi.abs();
                String side = szi.signum() > 0 ? "LONG" : "SHORT";
                BigDecimal markPrice = (positionValue != null && size.signum() > 0)
                        ? positionValue.divide(size, 8, RoundingMode.HALF_UP)
                        : null;

                positions.add(new Position(coin, side, size, entryPx, leverage, unrealizedPnl, markPrice));
            }
        } catch (Exception e) {
            log.error("[HyperliquidPositionService] Failed to fetch positions for {}: {}", address, e.getMessage());
        }
        return positions;
    }

    private static BigDecimal toBigDecimal(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
