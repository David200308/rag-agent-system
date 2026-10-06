package com.agentsystem.webrisk.service.impl;

import com.agentsystem.config.WebRiskProperties;
import com.agentsystem.webrisk.entity.WebRiskUrlVerdict;
import com.agentsystem.webrisk.repository.WebRiskUrlVerdictRepository;
import com.agentsystem.webrisk.service.GoogleWebRiskClient;
import com.agentsystem.webrisk.service.WebRiskVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebRiskServiceImplTest {

    static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    static final String URL = "https://Example.com/page/";
    static final String HASH = WebRiskServiceImpl.sha256("https://example.com/page");
    static final String REDIS_KEY = WebRiskServiceImpl.REDIS_KEY_PREFIX + HASH;

    @Mock GoogleWebRiskClient          google;
    @Mock StringRedisTemplate          redis;
    @Mock ValueOperations<String, String> redisOps;
    @Mock WebRiskUrlVerdictRepository  repository;

    WebRiskServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(redisOps);
        service = newService(new WebRiskProperties(true, "test-key", 2000, 60, 24, 100));
    }

    WebRiskServiceImpl newService(WebRiskProperties props) {
        return new WebRiskServiceImpl(props, google, redis, repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void noApiKey_returnsUncheckedWithoutTouchingAnyTier() {
        service = newService(new WebRiskProperties(true, "", 0, 0, 0, 0));

        WebRiskVerdict v = service.check(URL);

        assertThat(v.safe()).isTrue();
        assertThat(v.expiresAt()).isNull();
        verifyNoInteractions(google, redis, repository);
    }

    @Test
    void allTiersMiss_callsGoogle_andBackfillsRedisAndDatabase() throws Exception {
        when(google.lookup(URL)).thenReturn(new GoogleWebRiskClient.LookupResult(List.of(), null));

        WebRiskVerdict v = service.check(URL);

        assertThat(v.safe()).isTrue();
        assertThat(v.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
        verify(redisOps).set(eq(REDIS_KEY), eq("1||" + v.expiresAt().toEpochMilli()), eq(Duration.ofMinutes(60)));
        ArgumentCaptor<WebRiskUrlVerdict> row = ArgumentCaptor.forClass(WebRiskUrlVerdict.class);
        verify(repository).save(row.capture());
        assertThat(row.getValue().getUrlHash()).isEqualTo(HASH);
        assertThat(row.getValue().getHost()).isEqualTo("example.com");
        assertThat(row.getValue().isSafe()).isTrue();
    }

    @Test
    void secondCheck_isServedFromCaffeine() throws Exception {
        when(google.lookup(URL)).thenReturn(new GoogleWebRiskClient.LookupResult(List.of(), null));

        service.check(URL);
        service.check("https://example.com/page");   // same normalized key

        verify(google, times(1)).lookup(anyString());
        verify(redisOps, times(1)).get(REDIS_KEY);   // second call never reached Redis
        verify(repository, times(1)).findByUrlHashAndExpiresAtAfter(HASH, NOW);
    }

    @Test
    void redisHit_skipsDatabaseAndGoogle() {
        Instant exp = NOW.plus(Duration.ofHours(3));
        when(redisOps.get(REDIS_KEY)).thenReturn("0|MALWARE,SOCIAL_ENGINEERING|" + exp.toEpochMilli());

        WebRiskVerdict v = service.check(URL);

        assertThat(v.safe()).isFalse();
        assertThat(v.threatTypes()).containsExactly("MALWARE", "SOCIAL_ENGINEERING");
        assertThat(v.expiresAt()).isEqualTo(exp);
        verifyNoInteractions(repository, google);
    }

    @Test
    void databaseHit_backfillsRedis_andSkipsGoogle() {
        Instant exp = NOW.plus(Duration.ofMinutes(10));
        when(repository.findByUrlHashAndExpiresAtAfter(HASH, NOW)).thenReturn(Optional.of(
                WebRiskUrlVerdict.builder().urlHash(HASH).safe(false).threatTypes("UNWANTED_SOFTWARE")
                        .expiresAt(exp).checkedAt(NOW).build()));

        WebRiskVerdict v = service.check(URL);

        assertThat(v.safe()).isFalse();
        assertThat(v.threatTypes()).containsExactly("UNWANTED_SOFTWARE");
        verify(redisOps).set(REDIS_KEY, "0|UNWANTED_SOFTWARE|" + exp.toEpochMilli(), Duration.ofMinutes(10));
        verifyNoInteractions(google);
    }

    @Test
    void threatMatch_usesGoogleExpireTime() throws Exception {
        Instant exp = NOW.plus(Duration.ofMinutes(30));
        when(google.lookup(URL)).thenReturn(new GoogleWebRiskClient.LookupResult(List.of("MALWARE"), exp));

        WebRiskVerdict v = service.check(URL);

        assertThat(v.safe()).isFalse();
        assertThat(v.expiresAt()).isEqualTo(exp);
    }

    @Test
    void threatMatch_withoutExpireTime_usesUnsafeTtl() throws Exception {
        when(google.lookup(URL)).thenReturn(new GoogleWebRiskClient.LookupResult(List.of("MALWARE"), null));

        assertThat(service.check(URL).expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void googleFailure_failsOpen_andCachesNothing() throws Exception {
        when(google.lookup(URL)).thenThrow(new IOException("Web Risk API returned HTTP 429"));

        WebRiskVerdict v = service.check(URL);

        assertThat(v.safe()).isTrue();
        assertThat(v.expiresAt()).isNull();
        verify(redisOps, never()).set(anyString(), anyString(), any(Duration.class));
        verify(repository, never()).save(any());

        service.check(URL);
        verify(google, times(2)).lookup(URL);       // unchecked verdicts aren't cached
    }

    @Test
    void redisAndDatabaseDown_stillAnswersFromGoogle() throws Exception {
        when(redisOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        doThrow(new RuntimeException("redis down")).when(redisOps).set(anyString(), anyString(), any(Duration.class));
        when(repository.findByUrlHashAndExpiresAtAfter(anyString(), any())).thenThrow(new RuntimeException("db down"));
        when(repository.save(any())).thenThrow(new RuntimeException("db down"));
        when(google.lookup(URL)).thenReturn(new GoogleWebRiskClient.LookupResult(List.of("SOCIAL_ENGINEERING"), null));

        WebRiskVerdict v = service.check(URL);

        assertThat(v.safe()).isFalse();
        assertThat(v.threatTypes()).containsExactly("SOCIAL_ENGINEERING");
    }

    @Test
    void expiredRedisValue_isIgnored() throws Exception {
        when(redisOps.get(REDIS_KEY)).thenReturn("1||" + NOW.minusSeconds(1).toEpochMilli());
        when(google.lookup(URL)).thenReturn(new GoogleWebRiskClient.LookupResult(List.of(), null));

        service.check(URL);

        verify(google).lookup(URL);
    }

    @Test
    void purgeExpired_deletesRowsOlderThanNow() {
        service.purgeExpired();
        verify(repository).deleteExpired(NOW);
    }
}
