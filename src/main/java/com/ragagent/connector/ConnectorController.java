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

    private final ConnectorService    connectorService;
    private final GoogleDocsService   googleDocsService;
    private final GoogleSheetsService googleSheetsService;
    private final GoogleSlidesService googleSlidesService;
    private final TelegramService     telegramService;

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

    /**
     * POST /api/v1/connectors/google/docs
     * Body: { "title": "...", "content": "..." }
     * Creates a Google Doc and returns { "url": "https://docs.google.com/..." }
     */
    @PostMapping("/google/docs")
    public ResponseEntity<Map<String, String>> createGoogleDoc(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String email   = (String) request.getAttribute("authenticatedEmail");
        String title   = body.getOrDefault("title", "Exported Document");
        String content = body.get("content");

        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String url = googleDocsService.createDocument(title, content, email);
        return ResponseEntity.ok(Map.of("url", url));
    }

    /** POST /api/v1/connectors/google/sheets — { title, content } → { url } */
    @PostMapping("/google/sheets")
    public ResponseEntity<Map<String, String>> createGoogleSheet(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String email   = (String) request.getAttribute("authenticatedEmail");
        String title   = body.getOrDefault("title", "Exported Spreadsheet");
        String content = body.get("content");
        if (content == null || content.isBlank()) return ResponseEntity.badRequest().build();

        String url = googleSheetsService.createSpreadsheet(title, content, email);
        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * POST /api/v1/connectors/telegram/webhook
     * Called by Telegram's servers when a user sends a message to the bot.
     * Exempt from JWT auth — Telegram sends updates without a user session.
     */
    @PostMapping("/telegram/webhook")
    public ResponseEntity<Void> telegramWebhook(@RequestBody java.util.Map<String, Object> update) {
        telegramService.handleWebhook(update);
        return ResponseEntity.ok().build();
    }

    /** POST /api/v1/connectors/google/slides — { title, content } → { url } */
    @PostMapping("/google/slides")
    public ResponseEntity<Map<String, String>> createGoogleSlides(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String email   = (String) request.getAttribute("authenticatedEmail");
        String title   = body.getOrDefault("title", "Exported Presentation");
        String content = body.get("content");
        if (content == null || content.isBlank()) return ResponseEntity.badRequest().build();

        String url = googleSlidesService.createPresentation(title, content, email);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
