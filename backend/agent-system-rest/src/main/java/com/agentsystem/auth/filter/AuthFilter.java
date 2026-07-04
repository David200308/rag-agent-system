package com.agentsystem.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentsystem.auth.AuthProperties;
import com.agentsystem.auth.service.AuthService;
import com.agentsystem.user.service.UserAccountService;
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

    private final AuthProperties     authProperties;
    private final AuthService        authService;
    private final UserAccountService userAccountService;
    private final ObjectMapper       objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!authProperties.enabled()) return true;

        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")
            || path.startsWith("/api/v1/share/")
            || path.startsWith("/api/v1/scheduler/")         // service-key auth, not JWT
            || path.matches(".*/connectors/[^/]+/exchange")  // server-to-server OAuth exchange
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

        String path  = request.getRequestURI();
        String token = extractToken(request);
        var    claims = (token != null) ? authService.validateTokenFull(token) : null;
        // Every table besides `users` still keys rows by plaintext email (Phase 2 will
        // migrate them to user_uuid), so resolve email here — via the Redis-cached
        // decrypt path — and keep populating the same "authenticatedEmail" attribute the
        // rest of the app already reads, rather than requiring every call site to change.
        String email = (claims != null) ? userAccountService.getEmailByUuid(claims.userUuid()) : null;

        // Connector routes are JWT-optional: a valid token sets the email so tokens
        // are stored/looked up under the real user, but the request is never blocked.
        boolean isConnectorPath = path.startsWith("/api/v1/connectors/");

        if (email == null && !isConnectorPath) {
            log.warn("[AuthFilter] Rejected unauthenticated request to {}", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    objectMapper.writeValueAsString(Map.of("error", "Unauthorized")));
            return;
        }

        if (claims != null) {
            request.setAttribute("authenticatedUserUuid", claims.userUuid());
            request.setAttribute("authenticatedEmail", email);
            request.setAttribute("authenticatedMode",  claims.mode());
            request.setAttribute("authenticatedOrgId", claims.orgId());
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
}
