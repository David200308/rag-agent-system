package com.agentsystem.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentsystem.auth.service.CliKeyService;
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
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CliSignatureFilterTest {

    @Mock CliKeyService      cliKeyService;
    @Mock HttpServletRequest  request;
    @Mock HttpServletResponse response;
    @Mock FilterChain         chain;

    CliSignatureFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CliSignatureFilter(cliKeyService, new ObjectMapper());
    }

    // ── shouldNotFilter ───────────────────────────────────────────────────────

    @Test
    void shouldNotFilter_authPath_returnsTrue() {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_sharePath_returnsTrue() {
        when(request.getRequestURI()).thenReturn("/api/v1/share/abc123");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_actuatorPath_returnsTrue() {
        when(request.getRequestURI()).thenReturn("/actuator/health");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_apiPath_returnsFalse() {
        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    // ── doFilterInternal — no header, pass through ────────────────────────────

    @Test
    void doFilter_noSignatureHeader_passesThrough() throws Exception {
        when(request.getHeader("X-Cli-Signature")).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void doFilter_blankSignatureHeader_passesThrough() throws Exception {
        when(request.getHeader("X-Cli-Signature")).thenReturn("  ");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ── doFilterInternal — missing required headers ───────────────────────────

    @Test
    void doFilter_missingTimestampHeader_rejects401() throws Exception {
        when(request.getHeader("X-Cli-Signature")).thenReturn("valid-sig");
        when(request.getHeader("X-Cli-Timestamp")).thenReturn(null);
        when(request.getHeader("X-Cli-Version")).thenReturn("1.0.0");
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@test.com");

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_missingVersionHeader_rejects401() throws Exception {
        when(request.getHeader("X-Cli-Signature")).thenReturn("valid-sig");
        when(request.getHeader("X-Cli-Timestamp")).thenReturn("12345");
        when(request.getHeader("X-Cli-Version")).thenReturn(null);
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@test.com");

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_missingEmail_rejects401() throws Exception {
        when(request.getHeader("X-Cli-Signature")).thenReturn("valid-sig");
        when(request.getHeader("X-Cli-Timestamp")).thenReturn("12345");
        when(request.getHeader("X-Cli-Version")).thenReturn("1.0.0");
        when(request.getAttribute("authenticatedEmail")).thenReturn(null);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    // ── doFilterInternal — invalid timestamp format ───────────────────────────

    @Test
    void doFilter_invalidTimestampFormat_rejects401() throws Exception {
        when(request.getHeader("X-Cli-Signature")).thenReturn("valid-sig");
        when(request.getHeader("X-Cli-Timestamp")).thenReturn("not-a-number");
        when(request.getHeader("X-Cli-Version")).thenReturn("1.0.0");
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@test.com");

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    // ── doFilterInternal — signature verification ─────────────────────────────

    @Test
    void doFilter_validSignature_passesThrough() throws Exception {
        long ts = Instant.now().getEpochSecond();
        when(request.getHeader("X-Cli-Signature")).thenReturn("valid-sig");
        when(request.getHeader("X-Cli-Timestamp")).thenReturn(String.valueOf(ts));
        when(request.getHeader("X-Cli-Version")).thenReturn("1.0.0");
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@test.com");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");
        when(cliKeyService.verify(anyString(), anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(request).setAttribute(CliSignatureFilter.REQUEST_SOURCE_ATTR, CliSignatureFilter.REQUEST_SOURCE_CLI);
    }

    @Test
    void doFilter_invalidSignature_rejects401() throws Exception {
        long ts = Instant.now().getEpochSecond();
        when(request.getHeader("X-Cli-Signature")).thenReturn("bad-sig");
        when(request.getHeader("X-Cli-Timestamp")).thenReturn(String.valueOf(ts));
        when(request.getHeader("X-Cli-Version")).thenReturn("1.0.0");
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@test.com");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");
        when(cliKeyService.verify(anyString(), anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(false);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }
}
