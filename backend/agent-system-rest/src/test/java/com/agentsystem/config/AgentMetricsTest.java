package com.ragagent.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMetricsTest {

    SimpleMeterRegistry registry;
    AgentMetrics        metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics  = new AgentMetrics(registry);
    }

    // ── recordQuery ───────────────────────────────────────────────────────────

    @Test
    void recordQuery_incrementsQueryCounter() {
        metrics.recordQuery(false, 100);
        metrics.recordQuery(false, 200);

        Counter query = registry.find("agent.system.query").counter();
        assertThat(query).isNotNull();
        assertThat(query.count()).isEqualTo(2.0);
    }

    @Test
    void recordQuery_fallbackActivated_incrementsFallbackCounter() {
        metrics.recordQuery(true, 150);

        Counter fallback = registry.find("agent.system.query.fallback").counter();
        assertThat(fallback).isNotNull();
        assertThat(fallback.count()).isEqualTo(1.0);
    }

    @Test
    void recordQuery_noFallback_doesNotIncrementFallbackCounter() {
        metrics.recordQuery(false, 100);

        Counter fallback = registry.find("agent.system.query.fallback").counter();
        assertThat(fallback).isNotNull();
        assertThat(fallback.count()).isEqualTo(0.0);
    }

    @Test
    void recordQuery_recordsDuration() {
        metrics.recordQuery(false, 500);

        Timer timer = registry.find("agent.system.query.duration").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);
    }

    // ── recordQueryError ──────────────────────────────────────────────────────

    @Test
    void recordQueryError_incrementsQueryAndErrorCounters() {
        metrics.recordQueryError();

        Counter query = registry.find("agent.system.query").counter();
        Counter error = registry.find("agent.system.query.error").counter();
        assertThat(query.count()).isEqualTo(1.0);
        assertThat(error.count()).isEqualTo(1.0);
    }

    @Test
    void recordQueryError_multipleTimes_accumulates() {
        metrics.recordQueryError();
        metrics.recordQueryError();
        metrics.recordQueryError();

        Counter error = registry.find("agent.system.query.error").counter();
        assertThat(error.count()).isEqualTo(3.0);
    }

    // ── recordIngest ──────────────────────────────────────────────────────────

    @Test
    void recordIngest_incrementsIngestCounter() {
        metrics.recordIngest();
        metrics.recordIngest();

        Counter ingest = registry.find("agent.system.ingest").counter();
        assertThat(ingest).isNotNull();
        assertThat(ingest.count()).isEqualTo(2.0);
    }

    // ── recordRateLimitRejection ──────────────────────────────────────────────

    @Test
    void recordRateLimitRejection_incrementsRateLimitCounter() {
        metrics.recordRateLimitRejection();

        Counter rejected = registry.find("agent.system.ratelimit.rejected").counter();
        assertThat(rejected).isNotNull();
        assertThat(rejected.count()).isEqualTo(1.0);
    }

    // ── all counters initialise at zero ───────────────────────────────────────

    @Test
    void allCounters_initialiseAtZero() {
        assertThat(registry.find("agent.system.query").counter().count()).isEqualTo(0.0);
        assertThat(registry.find("agent.system.query.fallback").counter().count()).isEqualTo(0.0);
        assertThat(registry.find("agent.system.query.error").counter().count()).isEqualTo(0.0);
        assertThat(registry.find("agent.system.ingest").counter().count()).isEqualTo(0.0);
        assertThat(registry.find("agent.system.ratelimit.rejected").counter().count()).isEqualTo(0.0);
    }
}
