package com.agentsystem.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentsystem.auth.AuthProperties;
import com.agentsystem.auth.service.AuthService;
import com.agentsystem.auth.service.JwtService;
import com.agentsystem.user.service.UserAccountService;
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
    @Mock UserAccountService userAccountService;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain        chain;

    AuthFilter filterEnabled;
    AuthFilter filterDisabled;

    private static final String TEST_EMAIL_KEY = "dGVzdC1lbWFpbC1lbmNyeXB0aW9uLWtleS0zMmJ5dGVz";

    @BeforeEach
    void setUp() {
        AuthProperties enabled  = new AuthProperties(true,  10, "secret-key", 24, TEST_EMAIL_KEY);
        AuthProperties disabled = new AuthProperties(false, 10, "secret-key", 24, TEST_EMAIL_KEY);
        filterEnabled  = new AuthFilter(enabled,  authService, userAccountService, new ObjectMapper());
        filterDisabled = new AuthFilter(disabled, authService, userAccountService, new ObjectMapper());
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

    private static final String USER_UUID = "11111111-1111-1111-1111-111111111111";

    private static JwtService.TokenClaims personal(String userUuid) {
        return new JwtService.TokenClaims(userUuid, "PERSONAL", null);
    }

    private static JwtService.TokenClaims team(String userUuid, String orgId) {
        return new JwtService.TokenClaims(userUuid, "TEAM", orgId);
    }

    @Test
    void doFilterInternal_validToken_setsAllAttributesAndContinues() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(authService.validateTokenFull("valid-token")).thenReturn(personal(USER_UUID));
        when(userAccountService.getEmailByUuid(USER_UUID)).thenReturn("user@example.com");

        filterEnabled.doFilterInternal(request, response, chain);

        verify(request).setAttribute("authenticatedUserUuid", USER_UUID);
        verify(request).setAttribute("authenticatedEmail", "user@example.com");
        verify(request).setAttribute("authenticatedMode", "PERSONAL");
        verify(request).setAttribute("authenticatedOrgId", null);
        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void doFilterInternal_teamToken_setsOrgIdAttribute() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");
        when(request.getHeader("Authorization")).thenReturn("Bearer team-token");
        when(authService.validateTokenFull("team-token")).thenReturn(team(USER_UUID, "skyproton"));
        when(userAccountService.getEmailByUuid(USER_UUID)).thenReturn("user@example.com");

        filterEnabled.doFilterInternal(request, response, chain);

        verify(request).setAttribute("authenticatedEmail", "user@example.com");
        verify(request).setAttribute("authenticatedMode", "TEAM");
        verify(request).setAttribute("authenticatedOrgId", "skyproton");
        verify(chain).doFilter(request, response);
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
        when(authService.validateTokenFull("bad-token")).thenReturn(null);
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
    void doFilterInternal_connectorPath_validToken_setsAllAttributesAndAllows() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/connectors/google/docs");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(authService.validateTokenFull("valid-token")).thenReturn(personal(USER_UUID));
        when(userAccountService.getEmailByUuid(USER_UUID)).thenReturn("user@example.com");

        filterEnabled.doFilterInternal(request, response, chain);

        verify(request).setAttribute("authenticatedEmail", "user@example.com");
        verify(request).setAttribute("authenticatedMode", "PERSONAL");
        verify(chain).doFilter(request, response);
    }
}
