package com.ragagent.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamModeRestrictionFilterTest {

    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain chain;

    TeamModeRestrictionFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TeamModeRestrictionFilter(new ObjectMapper());
    }

    // ── shouldNotFilter ───────────────────────────────────────────────────────

    @Test
    void shouldNotFilter_financialPath_returnsFalse() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/financial/cards");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void shouldNotFilter_nonFinancialPath_returnsTrue() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/travel");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_rootPath_returnsTrue() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/agent");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    // ── doFilterInternal ──────────────────────────────────────────────────────

    @Test
    void doFilterInternal_teamMode_returns403AndBlocksChain() throws Exception {
        when(request.getAttribute("authenticatedMode")).thenReturn("TEAM");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(response).setContentType("application/json");
        assertThat(sw.toString()).contains("Financial is not available in team mode");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_personalMode_passesThrough() throws Exception {
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void doFilterInternal_nullMode_passesThrough() throws Exception {
        when(request.getAttribute("authenticatedMode")).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
