package com.ragagent.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.config.AgentMetrics;
import com.ragagent.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Token-bucket rate limiter (Bucket4j) scoped per authenticated user email,
 * or per client IP for unauthenticated/shared paths.
 *
 * Runs at Order(2), immediately after AuthFilter (Order(1)), so the email
 * attribute is already set when this filter executes.
 *
 * Skipped for auth, scheduler, actuator, Swagger, and MCP paths —
 * the same exemption list as AuthFilter.
 *
 * Limits (all per-minute, configurable via env):
 *   AGENT_QUERY  — LLM query calls        (default 20/min)
 *   INGEST       — document ingest calls  (default 10/min)
 *   DEFAULT      — everything else        (default 100/min)
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties props;
    private final ObjectMapper        objectMapper;
    private final AgentMetrics        agentMetrics;

    // key = "<email|ip>:<CATEGORY>" → bucket
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")
            || path.startsWith("/api/v1/scheduler/")
            || path.startsWith("/api/v1/share/")
            || path.startsWith("/actuator/")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/swagger-ui")
            || path.startsWith("/mcp/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String category = categorize(request.getRequestURI());
        String key      = resolveKey(request) + ":" + category;

        Bucket           bucket = buckets.computeIfAbsent(key, k -> newBucket(category));
        ConsumptionProbe probe  = bucket.tryConsumeAndReturnRemaining(1);

        response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));

        if (probe.isConsumed()) {
            chain.doFilter(request, response);
        } else {
            long retryAfterSecs = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
            agentMetrics.recordRateLimitRejection();
            log.warn("[RateLimitFilter] limit exceeded key={} path={}", key, request.getRequestURI());
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfterSecs));
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(
                    Map.of("error", "Too Many Requests", "retryAfterSeconds", retryAfterSecs)));
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String resolveKey(HttpServletRequest request) {
        String email = (String) request.getAttribute("authenticatedEmail");
        if (email != null) return "user:" + email;
        return "ip:" + extractClientIp(request);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private String categorize(String path) {
        if (path.equals("/api/v1/agent/query"))          return "QUERY";
        if (path.startsWith("/api/v1/agent/ingest"))     return "INGEST";
        return "DEFAULT";
    }

    private Bucket newBucket(String category) {
        long capacity = switch (category) {
            case "QUERY"  -> props.agentQueryLimit();
            case "INGEST" -> props.ingestLimit();
            default       -> props.defaultLimit();
        };
        return Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.greedy(capacity, Duration.ofMinutes(1))))
                .build();
    }
}
