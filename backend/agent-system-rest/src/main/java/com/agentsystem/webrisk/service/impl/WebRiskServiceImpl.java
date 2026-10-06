package com.agentsystem.webrisk.service.impl;

import com.agentsystem.config.WebRiskProperties;
import com.agentsystem.webfetch.service.UrlNormalizer;
import com.agentsystem.webrisk.entity.WebRiskUrlVerdict;
import com.agentsystem.webrisk.repository.WebRiskUrlVerdictRepository;
import com.agentsystem.webrisk.service.GoogleWebRiskClient;
import com.agentsystem.webrisk.service.WebRiskService;
import com.agentsystem.webrisk.service.WebRiskVerdict;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Web Risk checks behind a three-tier verdict cache — each tier is consulted in order
 * and every miss is back-filled from the tier (or Google call) that answered:
 *
 *   L1 Caffeine (per instance, bounded)  →  L2 Redis (shared)  →  L3 MySQL (durable)  →  Google
 *
 * All tiers expire an entry at the verdict's {@code expiresAt}: Google's expireTime for a
 * threat match, {@code safe-ttl-minutes} for a clean URL. A failing cache tier is skipped
 * (logged at debug) rather than failing the check; a failing Google call fails open.
 */
@Slf4j
@Service
public class WebRiskServiceImpl implements WebRiskService {

    static final String REDIS_KEY_PREFIX = "webrisk:v1:";

    private final WebRiskProperties           props;
    private final GoogleWebRiskClient         google;
    private final StringRedisTemplate         redis;
    private final WebRiskUrlVerdictRepository repository;
    private final Clock                       clock;
    private final Cache<String, WebRiskVerdict> local;

    @Autowired
    public WebRiskServiceImpl(WebRiskProperties props, GoogleWebRiskClient google,
                              StringRedisTemplate redis, WebRiskUrlVerdictRepository repository) {
        this(props, google, redis, repository, Clock.systemUTC());
    }

    WebRiskServiceImpl(WebRiskProperties props, GoogleWebRiskClient google,
                       StringRedisTemplate redis, WebRiskUrlVerdictRepository repository, Clock clock) {
        this.props      = props;
        this.google     = google;
        this.redis      = redis;
        this.repository = repository;
        this.clock      = clock;
        this.local      = Caffeine.newBuilder()
                .maximumSize(props.localCacheMaxSize())
                .expireAfter(new VerdictExpiry(clock))
                .build();
    }

    @Override
    public WebRiskVerdict check(String url) {
        if (!props.active()) return WebRiskVerdict.unchecked();

        String normalized = UrlNormalizer.normalize(url);
        if (normalized == null) return WebRiskVerdict.unchecked();   // not http(s) — fetch rejects it anyway
        String hash = sha256(normalized);
        Instant now = clock.instant();

        WebRiskVerdict cached = local.getIfPresent(hash);
        if (cached != null && !cached.isExpired(now)) return cached;

        cached = fromRedis(hash, now);
        if (cached != null) {
            local.put(hash, cached);
            return cached;
        }

        cached = fromDatabase(hash, now);
        if (cached != null) {
            toRedis(hash, cached, now);
            local.put(hash, cached);
            return cached;
        }

        WebRiskVerdict fresh;
        try {
            GoogleWebRiskClient.LookupResult result = google.lookup(url);
            fresh = toVerdict(result, now);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return WebRiskVerdict.unchecked();
        } catch (Exception e) {
            log.warn("[WebRiskService] Lookup failed, allowing unchecked: {}", e.getMessage());
            return WebRiskVerdict.unchecked();
        }

        if (!fresh.safe()) {
            log.info("[WebRiskService] Threat {} for host {}", fresh.threatTypes(), URI.create(normalized).getHost());
        }
        toDatabase(hash, normalized, fresh, now);
        toRedis(hash, fresh, now);
        local.put(hash, fresh);
        return fresh;
    }

    /** Hourly purge of expired L3 rows (L1/L2 expire on their own). */
    @Scheduled(fixedDelay = 3_600_000)
    public void purgeExpired() {
        try {
            int deleted = repository.deleteExpired(clock.instant());
            if (deleted > 0) log.debug("[WebRiskService] Purged {} expired verdict(s)", deleted);
        } catch (Exception e) {
            log.warn("[WebRiskService] Purge failed: {}", e.getMessage());
        }
    }

    private WebRiskVerdict toVerdict(GoogleWebRiskClient.LookupResult result, Instant now) {
        if (result.threatTypes().isEmpty()) {
            return new WebRiskVerdict(true, List.of(), now.plus(Duration.ofMinutes(props.safeTtlMinutes())));
        }
        Instant expires = result.expireTime() != null && result.expireTime().isAfter(now)
                ? result.expireTime()
                : now.plus(Duration.ofHours(props.unsafeTtlHours()));
        return new WebRiskVerdict(false, result.threatTypes(), expires);
    }

    // ── L2: Redis — value "<1|0>|<threat,types>|<expiresAt epoch ms>" ──────────

    private WebRiskVerdict fromRedis(String hash, Instant now) {
        try {
            String value = redis.opsForValue().get(REDIS_KEY_PREFIX + hash);
            if (value == null) return null;
            String[] parts = value.split("\\|", -1);
            WebRiskVerdict v = new WebRiskVerdict("1".equals(parts[0]), splitTypes(parts[1]),
                    Instant.ofEpochMilli(Long.parseLong(parts[2])));
            return v.isExpired(now) ? null : v;
        } catch (Exception e) {
            log.debug("[WebRiskService] Redis read skipped: {}", e.getMessage());
            return null;
        }
    }

    private void toRedis(String hash, WebRiskVerdict v, Instant now) {
        try {
            Duration ttl = Duration.between(now, v.expiresAt());
            if (ttl.isNegative() || ttl.isZero()) return;
            String value = (v.safe() ? "1" : "0") + "|" + String.join(",", v.threatTypes())
                    + "|" + v.expiresAt().toEpochMilli();
            redis.opsForValue().set(REDIS_KEY_PREFIX + hash, value, ttl);
        } catch (Exception e) {
            log.debug("[WebRiskService] Redis write skipped: {}", e.getMessage());
        }
    }

    // ── L3: MySQL ───────────────────────────────────────────────────────────────

    private WebRiskVerdict fromDatabase(String hash, Instant now) {
        try {
            return repository.findByUrlHashAndExpiresAtAfter(hash, now)
                    .map(row -> new WebRiskVerdict(row.isSafe(), splitTypes(row.getThreatTypes()), row.getExpiresAt()))
                    .orElse(null);
        } catch (Exception e) {
            log.debug("[WebRiskService] DB read skipped: {}", e.getMessage());
            return null;
        }
    }

    private void toDatabase(String hash, String normalized, WebRiskVerdict v, Instant now) {
        try {
            repository.save(WebRiskUrlVerdict.builder()
                    .urlHash(hash)
                    .host(URI.create(normalized).getHost())
                    .safe(v.safe())
                    .threatTypes(String.join(",", v.threatTypes()))
                    .expiresAt(v.expiresAt())
                    .checkedAt(now)
                    .build());
        } catch (Exception e) {
            // e.g. a concurrent insert of the same hash from another instance — harmless
            log.debug("[WebRiskService] DB write skipped: {}", e.getMessage());
        }
    }

    private static List<String> splitTypes(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Caffeine per-entry expiry aligned to each verdict's own {@code expiresAt}. */
    private record VerdictExpiry(Clock clock) implements Expiry<String, WebRiskVerdict> {
        @Override
        public long expireAfterCreate(String key, WebRiskVerdict v, long currentTime) {
            return Math.max(0, Duration.between(clock.instant(), v.expiresAt()).toNanos());
        }

        @Override
        public long expireAfterUpdate(String key, WebRiskVerdict v, long currentTime, long currentDuration) {
            return expireAfterCreate(key, v, currentTime);
        }

        @Override
        public long expireAfterRead(String key, WebRiskVerdict v, long currentTime, long currentDuration) {
            return currentDuration;
        }
    }
}
