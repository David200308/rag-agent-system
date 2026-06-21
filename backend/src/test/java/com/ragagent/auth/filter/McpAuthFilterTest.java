package com.ragagent.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.config.McpProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpAuthFilterTest {

    @Mock HttpServletRequest  request;
    @Mock HttpServletResponse response;
    @Mock FilterChain         chain;

    ObjectMapper objectMapper = new ObjectMapper();

    // ── shouldNotFilter ───────────────────────────────────────────────────────

    @Test
    void shouldNotFilter_nonMcpPath_returnsTrue() {
        McpAuthFilter filter = new McpAuthFilter(new McpProperties("secret-key", null), objectMapper);
        when(request.getRequestURI()).thenReturn("/api/v1/agent/query");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_mcpPath_returnsFalse() {
        McpAuthFilter filter = new McpAuthFilter(new McpProperties("secret-key", null), objectMapper);
        when(request.getRequestURI()).thenReturn("/mcp/sse");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    // ── doFilterInternal — rejections ────────────────────────────────────────

    @Test
    void doFilterInternal_apiKeyNotConfigured_rejects() throws Exception {
        McpAuthFilter filter = new McpAuthFilter(new McpProperties(null, null), objectMapper);
        when(request.getRequestURI()).thenReturn("/mcp/sse");
        when(request.getHeader("Authorization")).thenReturn("Bearer secret-key");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    void doFilterInternal_missingAuthHeader_rejects() throws Exception {
        McpAuthFilter filter = new McpAuthFilter(new McpProperties("secret-key", null), objectMapper);
        when(request.getRequestURI()).thenReturn("/mcp/sse");
        when(request.getHeader("Authorization")).thenReturn(null);
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    void doFilterInternal_wrongKey_rejects() throws Exception {
        McpAuthFilter filter = new McpAuthFilter(new McpProperties("secret-key", null), objectMapper);
        when(request.getRequestURI()).thenReturn("/mcp/sse");
        when(request.getHeader("Authorization")).thenReturn("Bearer wrong-key");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    void doFilterInternal_nonBearerHeader_rejects() throws Exception {
        McpAuthFilter filter = new McpAuthFilter(new McpProperties("secret-key", null), objectMapper);
        when(request.getRequestURI()).thenReturn("/mcp/sse");
        when(request.getHeader("Authorization")).thenReturn("Basic secret-key");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    // ── doFilterInternal — success ────────────────────────────────────────────

    @Test
    void doFilterInternal_correctKey_proceeds() throws Exception {
        McpAuthFilter filter = new McpAuthFilter(new McpProperties("secret-key", null), objectMapper);
        when(request.getHeader("Authorization")).thenReturn("Bearer secret-key");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }
}
