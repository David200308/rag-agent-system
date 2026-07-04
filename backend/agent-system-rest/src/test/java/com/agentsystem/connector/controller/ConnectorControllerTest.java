package com.agentsystem.connector.controller;

import com.agentsystem.connector.service.ConnectorService;
import com.agentsystem.connector.service.GoogleCalendarService;
import com.agentsystem.connector.service.GoogleDocsService;
import com.agentsystem.connector.service.GoogleSheetsService;
import com.agentsystem.connector.service.GoogleSlidesService;
import com.agentsystem.connector.service.TelegramService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectorControllerTest {

    @Mock ConnectorService       connectorService;
    @Mock GoogleDocsService      googleDocsService;
    @Mock GoogleSheetsService    googleSheetsService;
    @Mock GoogleSlidesService    googleSlidesService;
    @Mock GoogleCalendarService  googleCalendarService;
    @Mock TelegramService        telegramService;
    @Mock HttpServletRequest     request;
    @InjectMocks ConnectorController controller;

    // ── authUrl ────────────────────────────────────────────────────────────────

    @Test
    void authUrl_returnsAuthUrl() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);
        when(connectorService.getAuthUrl("google", "user@example.com", null))
                .thenReturn("https://accounts.google.com/o/oauth2/auth?...");

        ResponseEntity<Map<String, String>> resp = controller.authUrl("google", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsKey("authUrl");
    }

    // ── exchange ───────────────────────────────────────────────────────────────

    @Test
    void exchange_success_returns200() {
        Map<String, String> body = Map.of("code", "auth-code", "state", "state-token");

        ResponseEntity<Void> resp = controller.exchange("google", body);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(connectorService).exchangeCode("google", "auth-code", "state-token");
    }

    @Test
    void exchange_missingCode_returns400() {
        ResponseEntity<Void> resp = controller.exchange("google", Map.of("state", "state-token"));
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(connectorService);
    }

    @Test
    void exchange_missingState_returns400() {
        ResponseEntity<Void> resp = controller.exchange("google", Map.of("code", "auth-code"));
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ── status ─────────────────────────────────────────────────────────────────

    @Test
    void status_returnsConnectionStatus() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);
        when(connectorService.getStatus("user@example.com", null))
                .thenReturn(Map.of("google", true, "telegram", false));

        ResponseEntity<Map<String, Boolean>> resp = controller.status(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("google", true);
    }

    // ── disconnect ─────────────────────────────────────────────────────────────

    @Test
    void disconnect_returns204() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);

        ResponseEntity<Void> resp = controller.disconnect("google", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
        verify(connectorService).disconnect("google", "user@example.com", null);
    }

    // ── telegramConfig ─────────────────────────────────────────────────────────

    @Test
    void telegramConfig_returnsBotUsername() {
        when(telegramService.getBotUsername()).thenReturn("mybot");

        ResponseEntity<Map<String, String>> resp = controller.telegramConfig();

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("botUsername", "mybot");
    }

    // ── telegramConnect ────────────────────────────────────────────────────────

    @Test
    void telegramConnect_success_returns200() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);
        Map<String, Object> authData = Map.of("id", 12345L, "hash", "abc");

        ResponseEntity<Void> resp = controller.telegramConnect(authData, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(telegramService).validateAndConnect(authData, "user@example.com", null);
    }

    @Test
    void telegramConnect_invalidHash_returns400() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);
        doThrow(new IllegalArgumentException("invalid hash"))
                .when(telegramService).validateAndConnect(any(), anyString(), any());

        ResponseEntity<Void> resp = controller.telegramConnect(Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ── createGoogleDoc ────────────────────────────────────────────────────────

    @Test
    void createGoogleDoc_blankContent_returns400() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);

        ResponseEntity<Map<String, String>> resp = controller.createGoogleDoc(
                Map.of("title", "Test", "content", " "), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void createGoogleDoc_success_returnsUrl() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);
        when(googleDocsService.createDocument("Test Doc", "Hello world", "user@example.com", null))
                .thenReturn("https://docs.google.com/document/d/abc");

        ResponseEntity<Map<String, String>> resp = controller.createGoogleDoc(
                Map.of("title", "Test Doc", "content", "Hello world"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsKey("url");
    }

    @Test
    void createGoogleDoc_missingContent_returns400() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);

        ResponseEntity<Map<String, String>> resp = controller.createGoogleDoc(
                Map.of("title", "Test Doc"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ── createGoogleSheet ──────────────────────────────────────────────────────

    @Test
    void createGoogleSheet_success_returnsUrl() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);
        when(googleSheetsService.createSpreadsheet(anyString(), anyString(), anyString(), any()))
                .thenReturn("https://docs.google.com/spreadsheets/d/abc");

        ResponseEntity<Map<String, String>> resp = controller.createGoogleSheet(
                Map.of("content", "col1,col2"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsKey("url");
    }

    @Test
    void createGoogleSheet_blankContent_returns400() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);

        ResponseEntity<Map<String, String>> resp = controller.createGoogleSheet(
                Map.of("content", ""), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ── createGoogleSlides ─────────────────────────────────────────────────────

    @Test
    void createGoogleSlides_success_returnsUrl() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);
        when(googleSlidesService.createPresentation(anyString(), anyString(), anyString(), any()))
                .thenReturn("https://docs.google.com/presentation/d/abc");

        ResponseEntity<Map<String, String>> resp = controller.createGoogleSlides(
                Map.of("content", "Slide content here"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void createGoogleSlides_blankContent_returns400() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);

        ResponseEntity<Map<String, String>> resp = controller.createGoogleSlides(
                Map.of("title", "Presentation"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ── listCalendarEvents ────────────────────────────────────────────────────

    @Test
    void listCalendarEvents_returnsEventsString() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);
        when(googleCalendarService.listEvents("user@example.com", null, 10))
                .thenReturn("Event 1, Event 2");

        ResponseEntity<Map<String, String>> resp = controller.listCalendarEvents(10, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("events", "Event 1, Event 2");
    }

    // ── createCalendarEvent ───────────────────────────────────────────────────

    @Test
    void createCalendarEvent_success_returns200() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);
        when(googleCalendarService.createEvent(
                eq("user@example.com"), isNull(), eq("Meeting"), eq("2024-01-01T10:00:00"),
                eq("2024-01-01T11:00:00"), isNull(), isNull()))
                .thenReturn("created");

        var body = Map.of("title", "Meeting",
                "startDateTime", "2024-01-01T10:00:00",
                "endDateTime", "2024-01-01T11:00:00");

        ResponseEntity<Map<String, String>> resp = controller.createCalendarEvent(body, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("result", "created");
    }

    @Test
    void createCalendarEvent_missingTitle_returns400() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);

        var body = new java.util.HashMap<String, String>();
        body.put("startDateTime", "2024-01-01T10:00:00");
        body.put("endDateTime", "2024-01-01T11:00:00");

        ResponseEntity<Map<String, String>> resp = controller.createCalendarEvent(body, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void createCalendarEvent_missingStartDateTime_returns400() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);

        var body = new java.util.HashMap<String, String>();
        body.put("title", "Meeting");
        body.put("endDateTime", "2024-01-01T11:00:00");

        ResponseEntity<Map<String, String>> resp = controller.createCalendarEvent(body, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }
}
