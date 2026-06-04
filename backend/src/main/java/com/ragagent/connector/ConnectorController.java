package com.ragagent.connector;

import com.ragagent.org.OrgContext;
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

    private final ConnectorService       connectorService;
    private final GoogleDocsService      googleDocsService;
    private final GoogleSheetsService    googleSheetsService;
    private final GoogleSlidesService    googleSlidesService;
    private final GoogleCalendarService  googleCalendarService;
    private final TelegramService        telegramService;

    @GetMapping("/{provider}/auth-url")
    public ResponseEntity<Map<String, String>> authUrl(
            @PathVariable String provider,
            HttpServletRequest request) {

        OrgContext ctx = OrgContext.from(request);
        String url = connectorService.getAuthUrl(provider, ctx.email(), ctx.orgId());
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
        OrgContext ctx = OrgContext.from(request);
        return ResponseEntity.ok(connectorService.getStatus(ctx.email(), ctx.orgId()));
    }

    @DeleteMapping("/{provider}")
    public ResponseEntity<Void> disconnect(
            @PathVariable String provider,
            HttpServletRequest request) {

        OrgContext ctx = OrgContext.from(request);
        connectorService.disconnect(provider, ctx.email(), ctx.orgId());
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

        OrgContext ctx = OrgContext.from(request);
        try {
            telegramService.validateAndConnect(authData, ctx.email(), ctx.orgId());
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

        OrgContext ctx  = OrgContext.from(request);
        String title   = body.getOrDefault("title", "Exported Document");
        String content = body.get("content");
        if (content == null || content.isBlank()) return ResponseEntity.badRequest().build();

        String url = googleDocsService.createDocument(title, content, ctx.email(), ctx.orgId());
        return ResponseEntity.ok(Map.of("url", url));
    }

    /** POST /api/v1/connectors/google/sheets — { title, content } → { url } */
    @PostMapping("/google/sheets")
    public ResponseEntity<Map<String, String>> createGoogleSheet(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        OrgContext ctx  = OrgContext.from(request);
        String title   = body.getOrDefault("title", "Exported Spreadsheet");
        String content = body.get("content");
        if (content == null || content.isBlank()) return ResponseEntity.badRequest().build();

        String url = googleSheetsService.createSpreadsheet(title, content, ctx.email(), ctx.orgId());
        return ResponseEntity.ok(Map.of("url", url));
    }

    /** POST /api/v1/connectors/google/slides — { title, content } → { url } */
    @PostMapping("/google/slides")
    public ResponseEntity<Map<String, String>> createGoogleSlides(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        OrgContext ctx  = OrgContext.from(request);
        String title   = body.getOrDefault("title", "Exported Presentation");
        String content = body.get("content");
        if (content == null || content.isBlank()) return ResponseEntity.badRequest().build();

        String url = googleSlidesService.createPresentation(title, content, ctx.email(), ctx.orgId());
        return ResponseEntity.ok(Map.of("url", url));
    }

    // ── Google Calendar ───────────────────────────────────────────────────────

    /** GET /api/v1/connectors/google/calendar/events?maxResults=10 — list upcoming events */
    @GetMapping("/google/calendar/events")
    public ResponseEntity<Map<String, String>> listCalendarEvents(
            @RequestParam(defaultValue = "10") int maxResults,
            HttpServletRequest request) {

        OrgContext ctx = OrgContext.from(request);
        String result = googleCalendarService.listEvents(ctx.email(), ctx.orgId(), maxResults);
        return ResponseEntity.ok(Map.of("events", result));
    }

    /** POST /api/v1/connectors/google/calendar/events — { title, startDateTime, endDateTime, description?, location? } */
    @PostMapping("/google/calendar/events")
    public ResponseEntity<Map<String, String>> createCalendarEvent(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        OrgContext ctx = OrgContext.from(request);
        String title         = body.get("title");
        String startDateTime = body.get("startDateTime");
        String endDateTime   = body.get("endDateTime");
        if (title == null || startDateTime == null || endDateTime == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "title, startDateTime and endDateTime are required"));
        }
        String result = googleCalendarService.createEvent(
                ctx.email(), ctx.orgId(), title, startDateTime, endDateTime,
                body.get("description"), body.get("location"));
        return ResponseEntity.ok(Map.of("result", result));
    }
}
