package com.agentsystem.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentsystem.config.McpProperties;
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
import java.security.MessageDigest;
import java.util.Map;

/**
 * Gates the MCP SSE transport (/mcp/**). AuthFilter, ClientIdentityFilter, and
 * RateLimitFilter all exempt /mcp/ because the MCP protocol carries no per-request
 * JWT — without a filter here, /mcp/sse is reachable by anyone with network access
 * and search_knowledge/ingest_url run with no access control whatsoever.
 *
 * Requires "Authorization: Bearer <mcp.api-key>" on every MCP request. An unset
 * mcp.api-key keeps /mcp/** fully closed rather than fully open.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class McpAuthFilter extends OncePerRequestFilter {

    private final McpProperties props;
    private final ObjectMapper  objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/mcp/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String configuredKey = props.apiKey();
        String providedKey   = extractToken(request);

        if (configuredKey == null || configuredKey.isBlank()
                || providedKey == null || !constantTimeEquals(configuredKey, providedKey)) {
            log.warn("[McpAuthFilter] Rejected unauthenticated MCP request to {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    objectMapper.writeValueAsString(Map.of("error", "Unauthorized")));
            return;
        }
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
