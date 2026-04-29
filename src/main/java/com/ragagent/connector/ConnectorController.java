package com.ragagent.connector;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoints for external service connectors (Google Workspace, Figma).
 *
 * All paths are exempt from JWT auth (see AuthFilter) — the exchange endpoint
 * is server-to-server; security comes from the OAuth state token.
 * Status / disconnect read the email from the JWT attribute when auth is enabled.
 *
 * GET    /api/v1/connectors/{provider}/auth-url   → { authUrl }
 * POST   /api/v1/connectors/{provider}/exchange   → 200 | 400
 * GET    /api/v1/connectors/status                → { google, figma }
 * DELETE /api/v1/connectors/{provider}            → 204
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/connectors")
@RequiredArgsConstructor
public class ConnectorController {

    private final ConnectorService connectorService;

    @GetMapping("/{provider}/auth-url")
    public ResponseEntity<Map<String, String>> authUrl(
            @PathVariable String provider,
            HttpServletRequest request) {

        String email = (String) request.getAttribute("authenticatedEmail");
        String url   = connectorService.getAuthUrl(provider, email);
        return ResponseEntity.ok(Map.of("authUrl", url));
    }

    @PostMapping("/{provider}/exchange")
    public ResponseEntity<Void> exchange(
            @PathVariable String provider,
            @RequestBody Map<String, String> body) {

        String code  = body.get("code");
        String state = body.get("state");
        if (code == null || state == null) {
            return ResponseEntity.badRequest().build();
        }
        connectorService.exchangeCode(provider, code, state);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> status(HttpServletRequest request) {
        String email = (String) request.getAttribute("authenticatedEmail");
        return ResponseEntity.ok(connectorService.getStatus(email));
    }

    @DeleteMapping("/{provider}")
    public ResponseEntity<Void> disconnect(
            @PathVariable String provider,
            HttpServletRequest request) {

        String email = (String) request.getAttribute("authenticatedEmail");
        connectorService.disconnect(provider, email);
        return ResponseEntity.noContent().build();
    }
}
