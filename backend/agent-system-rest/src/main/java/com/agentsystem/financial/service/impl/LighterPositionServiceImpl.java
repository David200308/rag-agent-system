package com.agentsystem.financial.service.impl;

import com.agentsystem.financial.service.LighterPositionService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches open perp positions for a Lighter (zklighter.elliot.ai) account via the public
 * account-lookup-by-address endpoint (no API key required — verified live, a bad address just
 * returns an error payload rather than requiring auth).
 *
 * Endpoint: GET https://mainnet.zklighter.elliot.ai/api/v1/account?by=l1_address&value=<address>
 *
 * Results are cached per-address for a short TTL, same pattern as HyperliquidPositionServiceImpl.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LighterPositionServiceImpl implements LighterPositionService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private record CacheEntry(List<Position> positions, Instant fetchedAt) {}

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public List<Position> fetchPositions(String l1Address) {
        if (l1Address == null || l1Address.isBlank()) return List.of();
        String key = l1Address.toLowerCase(Locale.ROOT);

        CacheEntry cached = cache.get(key);
        if (cached != null && Duration.between(cached.fetchedAt(), Instant.now()).compareTo(CACHE_TTL) <= 0) {
            return cached.positions();
        }

        List<Position> positions = fetchFromApi(l1Address);
        cache.put(key, new CacheEntry(positions, Instant.now()));
        return positions;
    }

    private List<Position> fetchFromApi(String l1Address) {
        List<Position> positions = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(l1Address, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://mainnet.zklighter.elliot.ai/api/v1/account?by=l1_address&value=" + encoded))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode accounts = root.path("accounts");
            if (!accounts.isArray()) return positions;

            for (JsonNode account : accounts) {
                JsonNode posList = account.path("positions");
                if (!posList.isArray()) continue;

                for (JsonNode p : posList) {
                    String coin = p.path("symbol").asText(null);
                    BigDecimal rawSize = toBigDecimal(p.path("position").asText(null));
                    if (coin == null || rawSize == null || rawSize.signum() == 0) continue;

                    String side = p.path("sign").asInt(1) >= 0 ? "LONG" : "SHORT";
                    BigDecimal size = rawSize.abs();

                    BigDecimal entryPrice = toBigDecimal(p.path("avg_entry_price").asText(null));
                    BigDecimal unrealizedPnl = toBigDecimal(p.path("unrealized_pnl").asText(null));
                    BigDecimal fundingSinceOpen = toBigDecimal(p.path("total_funding_paid_out").asText(null));
                    BigDecimal imf = toBigDecimal(p.path("initial_margin_fraction").asText(null));

                    BigDecimal liquidationPrice = toBigDecimal(p.path("liquidation_price").asText(null));
                    if (liquidationPrice != null && liquidationPrice.signum() == 0) liquidationPrice = null;

                    // Fixed entry-time margin: entryNotional x initial_margin_fraction. Available on
                    // every position regardless of margin mode, unlike the live allocated_margin field.
                    BigDecimal margin = (entryPrice != null && imf != null)
                            ? entryPrice.multiply(size).multiply(imf).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                            : null;
                    BigDecimal leverage = (imf != null && imf.signum() > 0)
                            ? BigDecimal.valueOf(100).divide(imf, 4, RoundingMode.HALF_UP)
                            : null;

                    BigDecimal positionValue = toBigDecimal(p.path("position_value").asText(null));
                    BigDecimal markPrice = (positionValue != null && size.signum() > 0)
                            ? positionValue.abs().divide(size, 8, RoundingMode.HALF_UP)
                            : null;

                    positions.add(new Position(coin, side, size, entryPrice, leverage, unrealizedPnl,
                            markPrice, margin, liquidationPrice, fundingSinceOpen));
                }
            }
        } catch (Exception e) {
            log.error("[LighterPositionService] Failed to fetch positions for {}: {}", l1Address, e.getMessage());
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
