package com.ragagent.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Central Micrometer meters for the RAG agent pipeline.
 * Exposed at /actuator/prometheus for Prometheus scraping.
 *
 * Prometheus names (Micrometer appends _total to counters, _seconds to timers):
 *   agent_system_query_total              — all query invocations
 *   agent_system_query_fallback_total     — queries that hit the fallback path
 *   agent_system_query_error_total        — queries that errored before producing a response
 *   agent_system_query_duration_seconds   — end-to-end query latency (p50/p95/p99 histogram)
 *   agent_system_ingest_total             — document ingestion calls
 *   agent_system_ratelimit_rejected_total — requests rejected by the rate limiter
 */
@Component
public class AgentMetrics {

    private final Counter query;
    private final Counter queryFallback;
    private final Counter queryError;
    private final Timer   queryDuration;
    private final Counter ingest;
    private final Counter rateLimitRejected;

    public AgentMetrics(MeterRegistry registry) {
        // NOTE: do NOT add a ".total" suffix — Prometheus appends _total automatically.
        this.query = Counter.builder("agent.system.query")
                .description("Total RAG agent queries")
                .register(registry);
        this.queryFallback = Counter.builder("agent.system.query.fallback")
                .description("Queries that activated the fallback path")
                .register(registry);
        this.queryError = Counter.builder("agent.system.query.error")
                .description("Queries that failed with an exception")
                .register(registry);
        this.queryDuration = Timer.builder("agent.system.query.duration")
                .description("End-to-end RAG query latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.ingest = Counter.builder("agent.system.ingest")
                .description("Total document ingestion operations")
                .register(registry);
        this.rateLimitRejected = Counter.builder("agent.system.ratelimit.rejected")
                .description("Requests rejected by the rate limiter")
                .register(registry);
    }

    public void recordQuery(boolean fallbackActivated, long durationMs) {
        query.increment();
        if (fallbackActivated) queryFallback.increment();
        queryDuration.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordQueryError() {
        query.increment();
        queryError.increment();
    }

    public void recordIngest() {
        ingest.increment();
    }

    public void recordRateLimitRejection() {
        rateLimitRejected.increment();
    }
}
