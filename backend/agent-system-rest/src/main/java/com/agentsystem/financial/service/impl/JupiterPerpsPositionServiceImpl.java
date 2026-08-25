package com.agentsystem.financial.service.impl;

import com.agentsystem.financial.service.JupiterPerpsPositionService;

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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches open perp positions for a Jupiter Perpetuals (Solana) wallet via the public
 * perps-api.jup.ag positions endpoint (no API key required).
 *
 * Endpoint: GET https://perps-api.jup.ag/v1/positions?walletAddress=<address>
 *
 * Unlike Hyperliquid's isolated margin — which absorbs unrealized PnL into the live margin balance —
 * Jupiter's `collateral` field was verified live to stay fixed at the entry-time amount:
 * collateral + pnlAfterFeesUsd == value, and pnlChangePctAfterFees == pnl / collateral exactly.
 * So `collateral` can be used directly as the "invested" basis, no PnL back-out needed here.
 *
 * Results are cached per-address for a short TTL, same pattern as HyperliquidPositionServiceImpl.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JupiterPerpsPositionServiceImpl implements JupiterPerpsPositionService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    // Jupiter Perps only lists these three markets (mint -> symbol).
    private static final Map<String, String> MARKET_SYMBOLS = Map.of(
            "So11111111111111111111111111111111111111112", "SOL",
            "3NZ9JMVBmGAqocybic2c7LQCJScmgsAZ6vQqTDzcqmJh", "BTC",
            "7vfCXTUXx5WJV5JADk17DUJ4ksgau7utNKj4b963voxs", "ETH"
    );

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private record CacheEntry(List<Position> positions, Instant fetchedAt) {}

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public List<Position> fetchPositions(String walletAddress) {
        if (walletAddress == null || walletAddress.isBlank()) return List.of();
        String key = walletAddress.trim();

        CacheEntry cached = cache.get(key);
        if (cached != null && Duration.between(cached.fetchedAt(), Instant.now()).compareTo(CACHE_TTL) <= 0) {
            return cached.positions();
        }

        List<Position> positions = fetchFromApi(key);
        cache.put(key, new CacheEntry(positions, Instant.now()));
        return positions;
    }

    private List<Position> fetchFromApi(String walletAddress) {
        List<Position> positions = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(walletAddress, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://perps-api.jup.ag/v1/positions?walletAddress=" + encoded))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode dataList = root.path("dataList");
            if (!dataList.isArray()) return positions;

            for (JsonNode p : dataList) {
                String marketMint = p.path("marketMint").asText(null);
                if (marketMint == null) continue;
                String coin = MARKET_SYMBOLS.getOrDefault(marketMint, marketMint);

                BigDecimal sizeUsd = toBigDecimal(p.path("size").asText(null));
                BigDecimal entryPrice = toBigDecimal(p.path("entryPrice").asText(null));
                if (sizeUsd == null || sizeUsd.signum() == 0 || entryPrice == null || entryPrice.signum() == 0) continue;

                String side = "short".equalsIgnoreCase(p.path("side").asText("")) ? "SHORT" : "LONG";

                // Jupiter reports size as USD notional, not token units — derive the token-unit size
                // from notional / entry price rather than assuming per-mint decimal counts.
                BigDecimal size = sizeUsd.divide(entryPrice, 8, RoundingMode.HALF_UP);

                BigDecimal leverage = toBigDecimal(p.path("leverage").asText(null));
                BigDecimal markPrice = toBigDecimal(p.path("markPrice").asText(null));
                BigDecimal margin = toBigDecimal(p.path("collateral").asText(null));
                BigDecimal liquidationPrice = toBigDecimal(p.path("liquidationPrice").asText(null));
                BigDecimal unrealizedPnl = toBigDecimal(p.path("pnlAfterFeesUsd").asText(null));
                BigDecimal borrowFeesUsd = toBigDecimal(p.path("borrowFeesUsd").asText(null));
                BigDecimal fundingSinceOpen = borrowFeesUsd != null ? borrowFeesUsd.negate() : null;
                BigDecimal roePercent = toBigDecimal(p.path("pnlChangePctAfterFees").asText(null));

                positions.add(new Position(coin, side, size, entryPrice, leverage, unrealizedPnl,
                        markPrice, margin, liquidationPrice, fundingSinceOpen, roePercent));
            }
        } catch (Exception e) {
            log.error("[JupiterPerpsPositionService] Failed to fetch positions for {}: {}", walletAddress, e.getMessage());
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
