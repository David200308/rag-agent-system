package com.ragagent.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.auth.AuthProperties;
import com.ragagent.auth.service.AuthService;
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
import java.util.Map;

/**
 * Guards all API endpoints when {@code auth.enabled=true}.
 *
 * Exempt paths (always allowed):
 *  - /api/v1/auth/**     (login endpoints)
 *  - /actuator/**        (health checks)
 *  - /v3/api-docs/**     (Swagger)
 *  - /swagger-ui/**
 *  - /mcp/**             (MCP SSE transport)
 *
 * Expects:  Authorization: Bearer <JWT>
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private final AuthProperties authProperties;
    private final AuthService    authService;
    private final ObjectMapper   objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!authProperties.enabled()) return true;

        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")
            || path.startsWith("/api/v1/share/")
            || path.startsWith("/api/v1/scheduler/")   // service-key auth, not JWT
            || path.startsWith("/actuator/")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/swagger-ui")
            || path.startsWith("/mcp/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // ── Gateway key path (agent-openapi service-to-service auth) ──────────
        // If X-Gateway-Key is present, validate it against auth.gateway-key.
        // On success, trust X-Key-Owner as the authenticated identity (no JWT needed).
        String gatewayKey = request.getHeader("X-Gateway-Key");
        if (gatewayKey != null) {
            String configured = authProperties.gatewayKey();
            if (configured != null && !configured.isBlank() && configured.equals(gatewayKey)) {
                String owner = request.getHeader("X-Key-Owner");
                if (owner != null && !owner.isBlank()) {
                    request.setAttribute("authenticatedEmail", owner);
                    chain.doFilter(request, response);
                    return;
                }
            }
            log.warn("[AuthFilter] Rejected request with invalid X-Gateway-Key on {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(Map.of("error", "Unauthorized")));
            return;
        }

        // ── Normal JWT path ───────────────────────────────────────────────────
        String token = extractToken(request);
        String email = (token != null) ? authService.validateToken(token) : null;

        if (email == null) {
            log.warn("[AuthFilter] Rejected unauthenticated request to {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    objectMapper.writeValueAsString(Map.of("error", "Unauthorized")));
            return;
        }

        request.setAttribute("authenticatedEmail", email);
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
