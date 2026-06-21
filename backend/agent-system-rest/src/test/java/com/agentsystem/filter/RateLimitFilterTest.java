package com.agentsystem.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentsystem.config.AgentMetrics;
import com.agentsystem.config.RateLimitProperties;
import io.github.bucket4j.TimeMeter;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.remote.CommandResult;
import io.github.bucket4j.distributed.remote.Request;
import io.github.bucket4j.mock.ProxyManagerMock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock FilterChain chain;

    RateLimitFilter filter;
    AgentMetrics    agentMetrics;

    /**
     * Bucket4j's own in-memory ProxyManager test double — real bucket logic, no Redis needed.
     *
     * {@link ProxyManagerMock} keys its internal state map by {@code byte[]} identity, but
     * {@code RateLimitFilter} calls {@code key.getBytes(...)} fresh on every request, so two
     * requests for the same logical key would never hit the same map entry. Real Redis doesn't
     * have this problem (it compares key bytes, not Java object references) — this override just
     * canonicalizes equal-content byte[] keys so the mock behaves the way Redis actually would.
     */
    private static ProxyManager<byte[]> newProxyManager() {
        Map<String, byte[]> canonicalKeys = new ConcurrentHashMap<>();
        return new ProxyManagerMock<byte[]>(TimeMeter.SYSTEM_MILLISECONDS) {
            @Override
            public <T> CommandResult<T> execute(byte[] key, Request<T> request) {
                byte[] canonicalKey = canonicalKeys.computeIfAbsent(
                        new String(key, StandardCharsets.UTF_8), k -> key);
                return super.execute(canonicalKey, request);
            }
        };
    }

    @BeforeEach
    void setUp() {
        RateLimitProperties props = new RateLimitProperties(20, 10, 100, 5, false);
        agentMetrics = new AgentMetrics(new SimpleMeterRegistry());
        filter = new RateLimitFilter(props, new ObjectMapper(), agentMetrics, newProxyManager());
    }

    // ── shouldNotFilter ───────────────────────────────────────────────────────

    @Test
    void shouldNotFilter_authPath_returnsFalse() throws Exception {
        // /api/v1/auth/** is no longer exempt — OTP request/verify get their own
        // tight bucket below instead of running unthrottled.
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/auth/verify-otp");
        assertThat(filter.shouldNotFilter(req)).isFalse();
    }

    @Test
    void shouldNotFilter_schedulerPath_returnsTrue() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/scheduler/trigger");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilter_sharePath_returnsTrue() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/share/abc123");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilter_actuatorPath_returnsTrue() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/actuator/health");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilter_swaggerPath_returnsTrue() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/swagger-ui/index.html");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilter_mcpPath_returnsTrue() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/mcp/sse");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilter_v3ApiDocsPath_returnsTrue() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/v3/api-docs");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilter_agentQueryPath_returnsFalse() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/agent/query");
        assertThat(filter.shouldNotFilter(req)).isFalse();
    }

    @Test
    void shouldNotFilter_ingestPath_returnsFalse() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/agent/ingest");
        assertThat(filter.shouldNotFilter(req)).isFalse();
    }

    // ── categorize ────────────────────────────────────────────────────────────

    @Test
    void categorize_queryPath_returnsQUERY() throws Exception {
        assertThat(callCategorize("/api/v1/agent/query")).isEqualTo("QUERY");
    }

    @Test
    void categorize_ingestPath_returnsINGEST() throws Exception {
        assertThat(callCategorize("/api/v1/agent/ingest")).isEqualTo("INGEST");
    }

    @Test
    void categorize_ingestSubPath_returnsINGEST() throws Exception {
        assertThat(callCategorize("/api/v1/agent/ingest/text")).isEqualTo("INGEST");
    }

    @Test
    void categorize_otherPath_returnsDEFAULT() throws Exception {
        assertThat(callCategorize("/api/v1/workflows")).isEqualTo("DEFAULT");
        assertThat(callCategorize("/api/v1/models")).isEqualTo("DEFAULT");
    }

    @Test
    void categorize_requestOtpPath_returnsOTP() throws Exception {
        assertThat(callCategorize("/api/v1/auth/request-otp")).isEqualTo("OTP");
    }

    @Test
    void categorize_verifyOtpPath_returnsOTP() throws Exception {
        assertThat(callCategorize("/api/v1/auth/verify-otp")).isEqualTo("OTP");
    }

    @Test
    void categorize_otherAuthPath_returnsDEFAULT() throws Exception {
        assertThat(callCategorize("/api/v1/auth/logout")).isEqualTo("DEFAULT");
    }

    // ── doFilterInternal — happy path ─────────────────────────────────────────

    @Test
    void doFilterInternal_firstRequest_isAllowed() throws Exception {
        MockHttpServletRequest  req  = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.setRequestURI("/api/v1/agent/query");
        req.setAttribute("authenticatedEmail", "user@test.com");

        filter.doFilterInternal(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertThat(resp.getStatus()).isNotEqualTo(429);
    }

    @Test
    void doFilterInternal_setsRateLimitHeader() throws Exception {
        MockHttpServletRequest  req  = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.setRequestURI("/api/v1/agent/query");
        req.setAttribute("authenticatedEmail", "user@test.com");

        filter.doFilterInternal(req, resp, chain);

        assertThat(resp.getHeader("X-RateLimit-Remaining")).isNotNull();
    }

    @Test
    void doFilterInternal_unauthenticatedRequest_usesIpBucket() throws Exception {
        MockHttpServletRequest  req  = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.setRequestURI("/api/v1/agent/query");
        req.setRemoteAddr("192.168.1.100");
        // No "authenticatedEmail" attribute

        filter.doFilterInternal(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    @Test
    void doFilterInternal_xForwardedFor_presentButUntrusted_doesNotThrow() throws Exception {
        MockHttpServletRequest  req  = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.setRequestURI("/api/v1/models");
        req.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");

        filter.doFilterInternal(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    // ── extractClientIp — trust-forwarded-for toggle ──────────────────────────

    @Test
    void extractClientIp_trustForwardedForDisabled_ignoresHeaderUsesRemoteAddr() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.9");
        req.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");

        assertThat(callExtractClientIp(filter, req)).isEqualTo("203.0.113.9");
    }

    @Test
    void extractClientIp_trustForwardedForEnabled_usesFirstHeaderIp() throws Exception {
        RateLimitProperties props = new RateLimitProperties(20, 10, 100, 5, true);
        RateLimitFilter trustingFilter = new RateLimitFilter(props, new ObjectMapper(), agentMetrics, newProxyManager());
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.9");
        req.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");

        assertThat(callExtractClientIp(trustingFilter, req)).isEqualTo("10.0.0.1");
    }

    @Test
    void extractClientIp_trustForwardedForEnabled_noHeader_fallsBackToRemoteAddr() throws Exception {
        RateLimitProperties props = new RateLimitProperties(20, 10, 100, 5, true);
        RateLimitFilter trustingFilter = new RateLimitFilter(props, new ObjectMapper(), agentMetrics, newProxyManager());
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.9");

        assertThat(callExtractClientIp(trustingFilter, req)).isEqualTo("203.0.113.9");
    }

    @Test
    void doFilterInternal_trustForwardedForDisabled_spoofedHeaderSharedAcrossDifferentClients_doesNotShareBucket() throws Exception {
        RateLimitProperties props = new RateLimitProperties(1, 1, 1, 1, false);
        RateLimitFilter limitFilter = new RateLimitFilter(props, new ObjectMapper(), agentMetrics, newProxyManager());

        MockHttpServletRequest reqA = new MockHttpServletRequest();
        reqA.setRequestURI("/api/v1/agent/query");
        reqA.setRemoteAddr("203.0.113.1");
        reqA.addHeader("X-Forwarded-For", "1.2.3.4"); // spoofed — same for both clients

        MockHttpServletRequest reqB = new MockHttpServletRequest();
        reqB.setRequestURI("/api/v1/agent/query");
        reqB.setRemoteAddr("203.0.113.2");
        reqB.addHeader("X-Forwarded-For", "1.2.3.4"); // spoofed — same for both clients

        MockHttpServletResponse respA = new MockHttpServletResponse();
        limitFilter.doFilterInternal(reqA, respA, chain);
        assertThat(respA.getStatus()).isNotEqualTo(429);

        // Different real client (remoteAddr) — must not be throttled by client A's spoofed XFF.
        MockHttpServletResponse respB = new MockHttpServletResponse();
        limitFilter.doFilterInternal(reqB, respB, chain);
        assertThat(respB.getStatus()).isNotEqualTo(429);
    }

    // ── rate limit exceeded — all tokens consumed ─────────────────────────────

    @Test
    void doFilterInternal_limitExceeded_returns429() throws Exception {
        // Use a very low limit so we can exhaust it
        RateLimitProperties props = new RateLimitProperties(1, 1, 1, 1, false);
        RateLimitFilter limitFilter = new RateLimitFilter(props, new ObjectMapper(), agentMetrics, newProxyManager());

        MockHttpServletRequest  req  = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.setRequestURI("/api/v1/agent/query");
        req.setAttribute("authenticatedEmail", "limited@test.com");

        // First request: allowed (consumes the 1 token)
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        limitFilter.doFilterInternal(req, resp1, chain);
        assertThat(resp1.getStatus()).isNotEqualTo(429);

        // Second request: rate limited (no tokens left)
        limitFilter.doFilterInternal(req, resp, chain);
        assertThat(resp.getStatus()).isEqualTo(429);
        assertThat(resp.getContentAsString()).contains("Too Many Requests");
        assertThat(resp.getHeader("Retry-After")).isNotNull();
    }

    @Test
    void doFilterInternal_otpBucketExceeded_returns429() throws Exception {
        RateLimitProperties props = new RateLimitProperties(20, 10, 100, 1, false);
        RateLimitFilter limitFilter = new RateLimitFilter(props, new ObjectMapper(), agentMetrics, newProxyManager());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/v1/auth/verify-otp");
        req.setRemoteAddr("203.0.113.5");

        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        limitFilter.doFilterInternal(req, resp1, chain);
        assertThat(resp1.getStatus()).isNotEqualTo(429);

        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        limitFilter.doFilterInternal(req, resp2, chain);
        assertThat(resp2.getStatus()).isEqualTo(429);
    }

    // ── reflection helper ─────────────────────────────────────────────────────

    private String callCategorize(String path) throws Exception {
        Method m = RateLimitFilter.class.getDeclaredMethod("categorize", String.class);
        m.setAccessible(true);
        return (String) m.invoke(filter, path);
    }

    private String callExtractClientIp(RateLimitFilter targetFilter, HttpServletRequest request) throws Exception {
        Method m = RateLimitFilter.class.getDeclaredMethod("extractClientIp", HttpServletRequest.class);
        m.setAccessible(true);
        return (String) m.invoke(targetFilter, request);
    }
}
