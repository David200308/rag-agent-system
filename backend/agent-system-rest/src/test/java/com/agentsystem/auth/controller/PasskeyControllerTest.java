package com.agentsystem.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentsystem.auth.service.PasskeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasskeyControllerTest {

    @Mock PasskeyService passkeyService;

    PasskeyController controller;

    @BeforeEach
    void setUp() {
        controller = new PasskeyController(passkeyService, new ObjectMapper());
    }

    // ── status ─────────────────────────────────────────────────────────────────

    @Test
    void status_blankEmail_returns400() {
        var resp = controller.status("  ");
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void status_hasPasskey_returnsTrue() {
        when(passkeyService.hasPasskey("user@example.com")).thenReturn(true);
        var resp = controller.status("user@example.com");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("hasPasskey", true);
    }

    @Test
    void status_noPasskey_returnsFalse() {
        when(passkeyService.hasPasskey("user@example.com")).thenReturn(false);
        var resp = controller.status("user@example.com");
        assertThat(resp.getBody()).containsEntry("hasPasskey", false);
    }

    @Test
    void status_normalisesEmailToLowercase() {
        when(passkeyService.hasPasskey("user@example.com")).thenReturn(true);
        controller.status("USER@EXAMPLE.COM");
        verify(passkeyService).hasPasskey("user@example.com");
    }

    // ── authenticateBegin ──────────────────────────────────────────────────────

    @Test
    void authenticateBegin_blankEmail_returns400() {
        var resp = controller.authenticateBegin(Map.of("email", ""));
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void authenticateBegin_missingEmail_returns400() {
        var resp = controller.authenticateBegin(Map.of());
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void authenticateBegin_unknownEmail_returns404() throws Exception {
        when(passkeyService.startAuthentication("notfound@example.com", "PERSONAL", null))
                .thenThrow(new IllegalArgumentException("No passkey registered"));
        var resp = controller.authenticateBegin(Map.of("email", "notfound@example.com"));
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void authenticateBegin_internalError_returns500() throws Exception {
        when(passkeyService.startAuthentication(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("unexpected"));
        var resp = controller.authenticateBegin(Map.of("email", "user@example.com"));
        assertThat(resp.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void authenticateBegin_success_returnsOptionsJson() throws Exception {
        when(passkeyService.startAuthentication("user@example.com", "PERSONAL", null))
                .thenReturn("{\"challenge\":\"abc123\"}");
        var resp = controller.authenticateBegin(Map.of("email", "user@example.com"));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void authenticateBegin_teamMode_passesOrgId() throws Exception {
        when(passkeyService.startAuthentication("user@example.com", "TEAM", "myorg"))
                .thenReturn("{\"challenge\":\"xyz\"}");
        var resp = controller.authenticateBegin(
                Map.of("email", "user@example.com", "mode", "TEAM", "orgId", "myorg"));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    // ── authenticateFinish ─────────────────────────────────────────────────────

    @Test
    void authenticateFinish_missingResponse_returns400() {
        var resp = controller.authenticateFinish(Map.of("email", "user@example.com"));
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void authenticateFinish_blankEmail_returns400() {
        var resp = controller.authenticateFinish(Map.of("email", "", "response", "{}"));
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void authenticateFinish_invalidCredential_returns401() throws Exception {
        when(passkeyService.finishAuthentication("user@example.com", "{}"))
                .thenThrow(new IllegalArgumentException("Verification failed"));
        var resp = controller.authenticateFinish(Map.of("email", "user@example.com", "response", "{}"));
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void authenticateFinish_invalidState_returns401() throws Exception {
        when(passkeyService.finishAuthentication("user@example.com", "{}"))
                .thenThrow(new IllegalStateException("Challenge mismatch"));
        var resp = controller.authenticateFinish(Map.of("email", "user@example.com", "response", "{}"));
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void authenticateFinish_unexpectedException_returns500() throws Exception {
        when(passkeyService.finishAuthentication(anyString(), anyString()))
                .thenThrow(new RuntimeException("DB error"));
        var resp = controller.authenticateFinish(Map.of("email", "user@example.com", "response", "{}"));
        assertThat(resp.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void authenticateFinish_success_returnsJwt() throws Exception {
        when(passkeyService.finishAuthentication("user@example.com", "{}")).thenReturn("jwt-token");
        var resp = controller.authenticateFinish(Map.of("email", "user@example.com", "response", "{}"));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("token", "jwt-token");
    }
}
