package com.ragagent.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.auth.service.CliKeyService;
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
 * Verifies the X-Cli-Signature header on CLI requests.
 *
 * Runs at Order(2), after AuthFilter (Order(1)) sets authenticatedEmail,
 * and before RateLimitFilter (Order(3)).
 *
 * If the header is present and the signature is invalid → 401.
 * If the header is present and valid → sets requestSource="CLI" attribute.
 * If the header is absent → no-op (browser / other clients pass through).
 *
 * Required headers:
 *   X-Cli-Signature  — Base64 Ed25519 signature
 *   X-Cli-Timestamp  — Unix seconds (must be within ±5 min of server time)
 *   X-Cli-Version    — CLI version string, e.g. "1.0.0"
 *
 * Canonical message signed by the CLI:
 *   "{X-Cli-Version} {METHOD} {/request/uri} {email} {X-Cli-Timestamp}"
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class CliSignatureFilter extends OncePerRequestFilter {

    public static final String REQUEST_SOURCE_ATTR = "requestSource";
    public static final String REQUEST_SOURCE_CLI  = "CLI";

    private final CliKeyService cliKeyService;
    private final ObjectMapper  objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Mirror AuthFilter's exemptions — these paths have no JWT, so there is
        // no authenticatedEmail attribute to look up the public key against.
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")
            || path.startsWith("/api/v1/share/")
            || path.startsWith("/api/v1/scheduler/")
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

        String signature = request.getHeader("X-Cli-Signature");
        if (signature == null || signature.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        String timestampHeader = request.getHeader("X-Cli-Timestamp");
        String cliVersion      = request.getHeader("X-Cli-Version");
        String email           = (String) request.getAttribute("authenticatedEmail");

        if (timestampHeader == null || cliVersion == null || email == null) {
            reject(response, "X-Cli-Signature present but X-Cli-Timestamp, X-Cli-Version, or auth token missing");
            return;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            reject(response, "X-Cli-Timestamp must be a Unix epoch integer");
            return;
        }

        boolean valid = cliKeyService.verify(
                email,
                signature,
                cliVersion,
                request.getMethod(),
                request.getRequestURI(),
                timestamp
        );

        if (!valid) {
            log.warn("[CliSignatureFilter] Invalid CLI signature from {} path={}", email, request.getRequestURI());
            reject(response, "Invalid CLI signature");
            return;
        }

        request.setAttribute(REQUEST_SOURCE_ATTR, REQUEST_SOURCE_CLI);
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                objectMapper.writeValueAsString(Map.of("error", reason)));
    }
}
