package com.ragagent.connector;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoints for external service connectors (Google Workspace, Figma, Telegram).
 *
 * GET    /api/v1/connectors/{provider}/auth-url   → { authUrl }            (Google, Figma)
 * POST   /api/v1/connectors/{provider}/exchange   → 200 | 400              (Google, Figma)
 * GET    /api/v1/connectors/status                → { google, figma, telegram }
 * DELETE /api/v1/connectors/{provider}            → 204
 *
 * GET    /api/v1/connectors/telegram/config       → { botUsername }
 * POST   /api/v1/connectors/telegram/connect      → 200 | 400  (Login Widget callback)
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

    // ── Telegram Login Widget ─────────────────────────────────────────────────

    /**
     * GET /api/v1/connectors/telegram/config
     * Returns the bot username so the frontend can render the Login Widget.
     * The bot token is never exposed to the client.
     */
    @GetMapping("/telegram/config")
    public ResponseEntity<Map<String, String>> telegramConfig() {
        return ResponseEntity.ok(Map.of("botUsername", telegramService.getBotUsername()));
    }

    /**
     * POST /api/v1/connectors/telegram/connect
     * Receives the Login Widget auth payload, validates the HMAC-SHA256 hash,
     * and stores the user's Telegram chat_id.
     *
     * Expected body: { id, first_name, last_name?, username?, photo_url?, auth_date, hash }
     */
    @PostMapping("/telegram/connect")
    public ResponseEntity<Void> telegramConnect(
            @RequestBody Map<String, Object> authData,
            HttpServletRequest request) {

        String email = (String) request.getAttribute("authenticatedEmail");
        try {
            telegramService.validateAndConnect(authData, email);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("[ConnectorController] Telegram connect rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    // ── Google Workspace ──────────────────────────────────────────────────────

    /** POST /api/v1/connectors/google/docs — { title, content } → { url } */
    @PostMapping("/google/docs")
    public ResponseEntity<Map<String, String>> createGoogleDoc(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String email   = (String) request.getAttribute("authenticatedEmail");
        String title   = body.getOrDefault("title", "Exported Document");
        String content = body.get("content");
        if (content == null || content.isBlank()) return ResponseEntity.badRequest().build();

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
