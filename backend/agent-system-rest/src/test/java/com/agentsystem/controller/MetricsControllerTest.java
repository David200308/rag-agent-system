package com.ragagent.controller;

import com.ragagent.config.AgentMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsControllerTest {

    SimpleMeterRegistry registry;
    MetricsController   controller;

    @BeforeEach
    void setUp() {
        registry   = new SimpleMeterRegistry();
        controller = new MetricsController(registry);
    }

    // ── metrics — empty registry ──────────────────────────────────────────────

    @Test
    void metrics_emptyRegistry_returnsZeroCounters() {
        ResponseEntity<Map<String, Object>> resp = controller.metrics();

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsEntry("queryTotal", 0.0);
        assertThat(body).containsEntry("queryFallbackTotal", 0.0);
        assertThat(body).containsEntry("queryErrorTotal", 0.0);
        assertThat(body).containsEntry("ingestTotal", 0.0);
        assertThat(body).containsEntry("rateLimitRejectedTotal", 0.0);
    }

    @Test
    void metrics_emptyRegistry_queryDurationHasZeroCount() {
        ResponseEntity<Map<String, Object>> resp = controller.metrics();

        @SuppressWarnings("unchecked")
        Map<String, Object> duration = (Map<String, Object>) resp.getBody().get("queryDuration");
        assertThat(duration).isNotNull();
        // When no timer exists, count is stored as Integer 0
        assertThat(((Number) duration.get("count")).intValue()).isEqualTo(0);
    }

    // ── metrics — with recorded events ───────────────────────────────────────

    @Test
    void metrics_afterRecordingQuery_showsCorrectCount() {
        AgentMetrics agentMetrics = new AgentMetrics(registry);
        agentMetrics.recordQuery(false, 150);
        agentMetrics.recordQuery(true, 200);

        ResponseEntity<Map<String, Object>> resp = controller.metrics();

        assertThat(resp.getBody()).containsEntry("queryTotal", 2.0);
        assertThat(resp.getBody()).containsEntry("queryFallbackTotal", 1.0);
    }

    @Test
    void metrics_afterRecordingError_showsQueryAndErrorCount() {
        AgentMetrics agentMetrics = new AgentMetrics(registry);
        agentMetrics.recordQueryError();

        ResponseEntity<Map<String, Object>> resp = controller.metrics();

        assertThat(resp.getBody()).containsEntry("queryTotal", 1.0);
        assertThat(resp.getBody()).containsEntry("queryErrorTotal", 1.0);
    }

    @Test
    void metrics_afterRecordingIngest_showsIngestCount() {
        AgentMetrics agentMetrics = new AgentMetrics(registry);
        agentMetrics.recordIngest();
        agentMetrics.recordIngest();

        ResponseEntity<Map<String, Object>> resp = controller.metrics();

        assertThat(resp.getBody()).containsEntry("ingestTotal", 2.0);
    }

    @Test
    void metrics_afterRateLimitRejection_showsRejectedCount() {
        AgentMetrics agentMetrics = new AgentMetrics(registry);
        agentMetrics.recordRateLimitRejection();

        ResponseEntity<Map<String, Object>> resp = controller.metrics();

        assertThat(resp.getBody()).containsEntry("rateLimitRejectedTotal", 1.0);
    }

    @Test
    void metrics_afterRecordingDuration_timerHasPositiveTotalTime() {
        AgentMetrics agentMetrics = new AgentMetrics(registry);
        agentMetrics.recordQuery(false, 500);

        ResponseEntity<Map<String, Object>> resp = controller.metrics();

        @SuppressWarnings("unchecked")
        Map<String, Object> duration = (Map<String, Object>) resp.getBody().get("queryDuration");
        assertThat(duration).containsKey("count");
        assertThat(duration).containsKey("totalMs");
        assertThat((long) duration.get("count")).isGreaterThan(0);
    }

    // ── metrics — result structure ────────────────────────────────────────────

    @Test
    void metrics_containsAllExpectedKeys() {
        ResponseEntity<Map<String, Object>> resp = controller.metrics();

        assertThat(resp.getBody()).containsKeys(
                "queryTotal",
                "queryFallbackTotal",
                "queryErrorTotal",
                "ingestTotal",
                "rateLimitRejectedTotal",
                "queryDuration"
        );
    }
}
