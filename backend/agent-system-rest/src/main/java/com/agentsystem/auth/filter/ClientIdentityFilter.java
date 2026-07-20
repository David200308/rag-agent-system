package com.agentsystem.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentsystem.auth.ClientIdentityProperties;
import com.agentsystem.auth.service.CliKeyService;
import com.agentsystem.auth.service.ClientIdentityService;
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
 * Enforces that every authenticated API request originates from a known client.
 *
 * Runs after AuthFilter (order=1). Checks for exactly one of:
 *
 *   CLI   → X-Cli-Signature + X-Cli-Timestamp + X-Cli-Version   (Ed25519, per-user key)
 *   iOS   → X-Mobile-Ios-Signature + X-Mobile-Ios-Timestamp + X-Mobile-Ios-Version (HMAC-SHA256)
 *   Web   → X-Web-Signature + X-Web-Timestamp + X-Web-Version   (HMAC-SHA256)
 *
 * Requests that present no recognised client header are rejected with 403.
 * Disabled by default via client.identity.enabled=false.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class ClientIdentityFilter extends OncePerRequestFilter {

    private final ClientIdentityProperties  props;
    private final ClientIdentityService     identityService;
    private final CliKeyService             cliKeyService;
    private final ObjectMapper              objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.enabled()) return true;

        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")
            || path.startsWith("/api/v1/share/")
            || path.startsWith("/api/v1/scheduler/")
            || path.equals("/api/v1/alerts/trigger")
            || path.matches(".*/connectors/[^/]+/exchange")
            || path.startsWith("/actuator/")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/swagger-ui")
            || path.startsWith("/mcp/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        String method = request.getMethod();
        String path   = request.getRequestURI();

        boolean valid = false;
        String  clientType = "unknown";

        String cliSig    = request.getHeader("X-Cli-Signature");
        String iosSig    = request.getHeader("X-Mobile-Ios-Signature");
        String webToken  = request.getHeader("X-Web-Token");

        if (cliSig != null) {
            clientType = "cli";
            String email    = (String) request.getAttribute("authenticatedEmail");
            String version  = request.getHeader("X-Cli-Version");
            String tsHeader = request.getHeader("X-Cli-Timestamp");
            if (email != null && version != null && tsHeader != null) {
                try {
                    valid = cliKeyService.verify(email, cliSig, version, method, path,
                            Long.parseLong(tsHeader));
                } catch (NumberFormatException ignored) {}
            }

        } else if (iosSig != null) {
            clientType = "ios";
            String version  = request.getHeader("X-Mobile-Ios-Version");
            String tsHeader = request.getHeader("X-Mobile-Ios-Timestamp");
            if (version != null && tsHeader != null) {
                valid = identityService.verifyIos(iosSig, tsHeader, version, method, path);
            }

        } else if (webToken != null) {
            // Web is server-to-server (Next.js → Spring Boot): static token comparison
            clientType = "web";
            valid = identityService.verifyWebToken(webToken);
        }

        if (!valid) {
            log.warn("[ClientIdentityFilter] Rejected request from client='{}' {} {}",
                    clientType, method, path);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    objectMapper.writeValueAsString(Map.of("error", "Forbidden: unrecognised client")));
            return;
        }

        log.debug("[ClientIdentityFilter] Verified {} client {} {}", clientType, method, path);
        chain.doFilter(request, response);
    }
}
