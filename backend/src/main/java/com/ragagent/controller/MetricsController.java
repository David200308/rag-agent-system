package com.ragagent.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Human-readable metrics endpoint for the RAG agent pipeline.
 *
 * GET /api/v1/metrics  — returns current counter and latency values as JSON.
 *
 * Complements /actuator/prometheus (Prometheus scrape format).
 * Requires authentication (handled by AuthFilter).
 */
@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
@Tag(name = "Metrics", description = "RAG agent pipeline metrics")
public class MetricsController {

    private final MeterRegistry registry;

    @GetMapping
    @Operation(summary = "Current RAG agent pipeline metrics (counts + latency percentiles)")
    public ResponseEntity<Map<String, Object>> metrics() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("queryTotal",              counterValue("agent.system.query"));
        result.put("queryFallbackTotal",      counterValue("agent.system.query.fallback"));
        result.put("queryErrorTotal",         counterValue("agent.system.query.error"));
        result.put("ingestTotal",             counterValue("agent.system.ingest"));
        result.put("rateLimitRejectedTotal",  counterValue("agent.system.ratelimit.rejected"));
        result.put("queryDuration",           timerSummary("agent.system.query.duration"));

        return ResponseEntity.ok(result);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private double counterValue(String name) {
        Counter c = registry.find(name).counter();
        return c != null ? c.count() : 0.0;
    }

    private Map<String, Object> timerSummary(String name) {
        Map<String, Object> summary = new LinkedHashMap<>();
        Timer timer = registry.find(name).timer();
        if (timer == null) {
            summary.put("count", 0);
            return summary;
        }
        summary.put("count",   timer.count());
        summary.put("totalMs", round(timer.totalTime(TimeUnit.MILLISECONDS)));
        summary.put("meanMs",  round(timer.mean(TimeUnit.MILLISECONDS)));
        for (var pv : timer.takeSnapshot().percentileValues()) {
            String key = "p" + (int) (pv.percentile() * 100) + "Ms";
            summary.put(key, round(pv.value(TimeUnit.MILLISECONDS)));
        }
        return summary;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
