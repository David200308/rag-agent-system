package com.ragagent.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.auth.AuthProperties;
import com.ragagent.auth.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    @Mock AuthService        authService;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain        chain;

    AuthFilter filterEnabled;
    AuthFilter filterDisabled;

    @BeforeEach
    void setUp() {
        AuthProperties enabled  = new AuthProperties(true,  10, "secret-key", 24);
        AuthProperties disabled = new AuthProperties(false, 10, "secret-key", 24);
        filterEnabled  = new AuthFilter(enabled,  authService, new ObjectMapper());
        filterDisabled = new AuthFilter(disabled, authService, new ObjectMapper());
    }

    // ── shouldNotFilter ────────────────────────────────────────────────────────

    @Test
    void shouldNotFilter_authDisabled_alwaysTrue() {
        // No stub needed — enabled=false causes immediate return before URI is read
        assertThat(filterDisabled.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_authPath_true() {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/request-otp");
        assertThat(filterEnabled.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_sharePath_true() {
        when(request.getRequestURI()).thenReturn("/api/v1/share/abc123");
        assertThat(filterEnabled.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_schedulerPath_true() {
        when(request.getRequestURI()).thenReturn("/api/v1/scheduler/trigger");
        assertThat(filterEnabled.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_actuatorPath_true() {
        when(request.getRequestURI()).thenReturn("/actuator/health");
        assertThat(filterEnabled.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_swaggerPath_true() {
        when(request.getRequestURI()).thenReturn("/swagger-ui/index.html");
        assertThat(filterEnabled.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_apiDocsPath_true() {
        when(request.getRequestURI()).thenReturn("/v3/api-docs/swagger-config");
        assertThat(filterEnabled.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_mcpPath_true() {
        when(request.getRequestURI()).thenReturn("/mcp/sse");
        assertThat(filterEnabled.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_protectedAgentPath_false() {
        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");
        assertThat(filterEnabled.shouldNotFilter(request)).isFalse();
    }

    // ── doFilterInternal ───────────────────────────────────────────────────────

    @Test
    void doFilterInternal_validToken_setsEmailAttributeAndContinues() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(authService.validateToken("valid-token")).thenReturn("user@example.com");

        filterEnabled.doFilterInternal(request, response, chain);

        verify(request).setAttribute("authenticatedEmail", "user@example.com");
        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void doFilterInternal_missingAuthHeader_returns401() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filterEnabled.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_invalidToken_returns401() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
        when(authService.validateToken("bad-token")).thenReturn(null);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filterEnabled.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_connectorPath_noToken_allowsThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/connectors/google/status");
        when(request.getHeader("Authorization")).thenReturn(null);

        filterEnabled.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void doFilterInternal_connectorPath_validToken_setsEmailAndAllows() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/connectors/google/docs");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(authService.validateToken("valid-token")).thenReturn("user@example.com");

        filterEnabled.doFilterInternal(request, response, chain);

        verify(request).setAttribute("authenticatedEmail", "user@example.com");
        verify(chain).doFilter(request, response);
    }
}
