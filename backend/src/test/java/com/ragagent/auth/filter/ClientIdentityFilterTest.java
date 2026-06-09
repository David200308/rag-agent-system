package com.ragagent.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.auth.ClientIdentityProperties;
import com.ragagent.auth.service.CliKeyService;
import com.ragagent.auth.service.ClientIdentityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientIdentityFilterTest {

    @Mock ClientIdentityService identityService;
    @Mock CliKeyService         cliKeyService;
    @Mock HttpServletRequest    request;
    @Mock HttpServletResponse   response;
    @Mock FilterChain           chain;

    // ── shouldNotFilter — disabled ────────────────────────────────────────────

    @Test
    void shouldNotFilter_featureDisabled_returnsTrue() {
        ClientIdentityFilter filter = makeFilter(false);

        // When disabled, shouldNotFilter = true → filter is skipped entirely without consulting the URI
        boolean result = filter.shouldNotFilter(request);
        assertThat(result).isTrue();
    }

    // ── shouldNotFilter — auth path ───────────────────────────────────────────

    @Test
    void shouldNotFilter_authPath_returnsTrue() {
        ClientIdentityFilter filter = makeFilter(true);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_apiPath_returnsFalse() {
        ClientIdentityFilter filter = makeFilter(true);
        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void shouldNotFilter_connectorExchangePath_returnsTrue() {
        ClientIdentityFilter filter = makeFilter(true);
        when(request.getRequestURI()).thenReturn("/api/connectors/google/exchange");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    // ── CLI client ────────────────────────────────────────────────────────────

    @Test
    void doFilter_cliSig_validSignature_passesThrough() throws Exception {
        ClientIdentityFilter filter = makeFilter(true);
        long ts = Instant.now().getEpochSecond();

        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");
        when(request.getHeader("X-Cli-Signature")).thenReturn("valid-sig");
        when(request.getHeader("X-Mobile-Ios-Signature")).thenReturn(null);
        when(request.getHeader("X-Web-Token")).thenReturn(null);
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@test.com");
        when(request.getHeader("X-Cli-Version")).thenReturn("1.0.0");
        when(request.getHeader("X-Cli-Timestamp")).thenReturn(String.valueOf(ts));
        when(request.getMethod()).thenReturn("GET");
        when(cliKeyService.verify(anyString(), anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void doFilter_cliSig_invalidSignature_rejects403() throws Exception {
        ClientIdentityFilter filter = makeFilter(true);
        long ts = Instant.now().getEpochSecond();

        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");
        when(request.getHeader("X-Cli-Signature")).thenReturn("bad-sig");
        when(request.getHeader("X-Mobile-Ios-Signature")).thenReturn(null);
        when(request.getHeader("X-Web-Token")).thenReturn(null);
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@test.com");
        when(request.getHeader("X-Cli-Version")).thenReturn("1.0.0");
        when(request.getHeader("X-Cli-Timestamp")).thenReturn(String.valueOf(ts));
        when(request.getMethod()).thenReturn("POST");
        when(cliKeyService.verify(anyString(), anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(false);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
    }

    // ── iOS client ────────────────────────────────────────────────────────────

    @Test
    void doFilter_iosSig_validSignature_passesThrough() throws Exception {
        ClientIdentityFilter filter = makeFilter(true);
        long ts = Instant.now().getEpochSecond();

        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");
        when(request.getHeader("X-Cli-Signature")).thenReturn(null);
        when(request.getHeader("X-Mobile-Ios-Signature")).thenReturn("ios-sig");
        when(request.getHeader("X-Web-Token")).thenReturn(null);
        when(request.getHeader("X-Mobile-Ios-Version")).thenReturn("2.0.0");
        when(request.getHeader("X-Mobile-Ios-Timestamp")).thenReturn(String.valueOf(ts));
        when(request.getMethod()).thenReturn("GET");
        when(identityService.verifyIos(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_iosSig_missingTimestamp_rejects403() throws Exception {
        ClientIdentityFilter filter = makeFilter(true);

        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");
        when(request.getHeader("X-Cli-Signature")).thenReturn(null);
        when(request.getHeader("X-Mobile-Ios-Signature")).thenReturn("ios-sig");
        when(request.getHeader("X-Web-Token")).thenReturn(null);
        when(request.getHeader("X-Mobile-Ios-Version")).thenReturn("2.0.0");
        when(request.getHeader("X-Mobile-Ios-Timestamp")).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    // ── Web client ────────────────────────────────────────────────────────────

    @Test
    void doFilter_webToken_validToken_passesThrough() throws Exception {
        ClientIdentityFilter filter = makeFilter(true);

        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");
        when(request.getHeader("X-Cli-Signature")).thenReturn(null);
        when(request.getHeader("X-Mobile-Ios-Signature")).thenReturn(null);
        when(request.getHeader("X-Web-Token")).thenReturn("secret-web-token");
        when(request.getMethod()).thenReturn("GET");
        when(identityService.verifyWebToken("secret-web-token")).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_webToken_invalidToken_rejects403() throws Exception {
        ClientIdentityFilter filter = makeFilter(true);

        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");
        when(request.getHeader("X-Cli-Signature")).thenReturn(null);
        when(request.getHeader("X-Mobile-Ios-Signature")).thenReturn(null);
        when(request.getHeader("X-Web-Token")).thenReturn("wrong-token");
        when(request.getMethod()).thenReturn("POST");
        when(identityService.verifyWebToken("wrong-token")).thenReturn(false);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
    }

    // ── No recognized client header ───────────────────────────────────────────

    @Test
    void doFilter_noClientHeader_rejects403() throws Exception {
        ClientIdentityFilter filter = makeFilter(true);

        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");
        when(request.getHeader("X-Cli-Signature")).thenReturn(null);
        when(request.getHeader("X-Mobile-Ios-Signature")).thenReturn(null);
        when(request.getHeader("X-Web-Token")).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    // ── CLI with invalid timestamp format ─────────────────────────────────────

    @Test
    void doFilter_cliSig_invalidTimestampFormat_rejects403() throws Exception {
        ClientIdentityFilter filter = makeFilter(true);

        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");
        when(request.getHeader("X-Cli-Signature")).thenReturn("sig");
        when(request.getHeader("X-Mobile-Ios-Signature")).thenReturn(null);
        when(request.getHeader("X-Web-Token")).thenReturn(null);
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@test.com");
        when(request.getHeader("X-Cli-Version")).thenReturn("1.0.0");
        when(request.getHeader("X-Cli-Timestamp")).thenReturn("not-a-number");
        when(request.getMethod()).thenReturn("GET");

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ClientIdentityFilter makeFilter(boolean enabled) {
        ClientIdentityProperties props = new ClientIdentityProperties(enabled, "ios-secret", "web-secret");
        return new ClientIdentityFilter(props, identityService, cliKeyService, new ObjectMapper());
    }
}
