package com.agentsystem.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentsystem.config.AgentMetrics;
import com.agentsystem.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Token-bucket rate limiter (Bucket4j) scoped per authenticated user email,
 * or per client IP for unauthenticated/shared paths.
 *
 * Buckets are persisted in Redis (via {@code bucketProxyManager}, see RedisConfig) so
 * the limit is shared across all backend instances instead of being per-instance.
 *
 * Runs at Order(3), after AuthFilter (Order(1)) and CliSignatureFilter (Order(2)),
 * so the email attribute is already set when this filter executes.
 *
 * Skipped for scheduler, share, actuator, Swagger, and MCP paths. Unlike
 * AuthFilter/ClientIdentityFilter, /api/v1/auth/** is NOT exempt here — OTP
 * request/verify are pre-auth (no authenticatedEmail yet, so keyed by IP) and
 * are the most brute-forceable endpoints in the system, so they get their own
 * tight OTP bucket instead of running unthrottled.
 *
 * Limits (all per-minute, configurable via env):
 *   AGENT_QUERY  — LLM query calls               (default 20/min)
 *   INGEST       — document ingest calls         (default 10/min)
 *   OTP          — /api/v1/auth/request-otp,verify-otp (default 5/min per IP)
 *   DEFAULT      — everything else               (default 100/min)
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties  props;
    private final ObjectMapper         objectMapper;
    private final AgentMetrics         agentMetrics;
    private final ProxyManager<byte[]> bucketProxyManager;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/scheduler/")
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

        BucketProxy bucket = bucketProxyManager.builder()
                .build(key.getBytes(StandardCharsets.UTF_8), () -> newConfiguration(category));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

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
        if (props.trustForwardedFor()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String categorize(String path) {
        if (path.equals("/api/v1/agent/query"))          return "QUERY";
        if (path.startsWith("/api/v1/agent/ingest"))     return "INGEST";
        if (path.equals("/api/v1/auth/request-otp")
                || path.equals("/api/v1/auth/verify-otp")) return "OTP";
        return "DEFAULT";
    }

    private BucketConfiguration newConfiguration(String category) {
        long capacity = switch (category) {
            case "QUERY"  -> props.agentQueryLimit();
            case "INGEST" -> props.ingestLimit();
            case "OTP"    -> props.otpLimit();
            default       -> props.defaultLimit();
        };
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.greedy(capacity, Duration.ofMinutes(1))))
                .build();
    }
}
