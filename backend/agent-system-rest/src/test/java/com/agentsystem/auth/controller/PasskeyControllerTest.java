package com.agentsystem.auth.controller;

import com.agentsystem.auth.AuthInnerClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Mock AuthInnerClient authInnerClient;

    PasskeyController controller;

    @BeforeEach
    void setUp() {
        controller = new PasskeyController(authInnerClient);
    }

    // ── status ─────────────────────────────────────────────────────────────────

    @Test
    void status_blankEmail_returns400() {
        var resp = controller.status("  ");
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void status_hasPasskey_returnsTrue() {
        when(authInnerClient.passkeyStatus("user@example.com")).thenReturn(true);
        var resp = controller.status("user@example.com");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("hasPasskey", true);
    }

    @Test
    void status_noPasskey_returnsFalse() {
        when(authInnerClient.passkeyStatus("user@example.com")).thenReturn(false);
        var resp = controller.status("user@example.com");
        assertThat(resp.getBody()).containsEntry("hasPasskey", false);
    }

    @Test
    void status_normalisesEmailToLowercase() {
        when(authInnerClient.passkeyStatus("user@example.com")).thenReturn(true);
        controller.status("USER@EXAMPLE.COM");
        verify(authInnerClient).passkeyStatus("user@example.com");
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
    void authenticateBegin_unknownEmail_returns404() {
        when(authInnerClient.passkeyAuthenticateBegin("notfound@example.com", "PERSONAL", null))
                .thenThrow(new IllegalArgumentException("No passkey registered"));
        var resp = controller.authenticateBegin(Map.of("email", "notfound@example.com"));
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void authenticateBegin_internalError_returns500() {
        when(authInnerClient.passkeyAuthenticateBegin(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("unexpected"));
        var resp = controller.authenticateBegin(Map.of("email", "user@example.com"));
        assertThat(resp.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void authenticateBegin_success_returnsOptionsJson() throws Exception {
        JsonNode options = new ObjectMapper().readTree("{\"challenge\":\"abc123\"}");
        when(authInnerClient.passkeyAuthenticateBegin("user@example.com", "PERSONAL", null))
                .thenReturn(options);
        var resp = controller.authenticateBegin(Map.of("email", "user@example.com"));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void authenticateBegin_teamMode_passesOrgId() throws Exception {
        JsonNode options = new ObjectMapper().readTree("{\"challenge\":\"xyz\"}");
        when(authInnerClient.passkeyAuthenticateBegin("user@example.com", "TEAM", "myorg"))
                .thenReturn(options);
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
    void authenticateFinish_invalidCredential_returns401() {
        when(authInnerClient.passkeyAuthenticateFinish("user@example.com", "{}"))
                .thenThrow(new IllegalArgumentException("Verification failed"));
        var resp = controller.authenticateFinish(Map.of("email", "user@example.com", "response", "{}"));
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void authenticateFinish_unexpectedException_returns500() {
        when(authInnerClient.passkeyAuthenticateFinish(anyString(), anyString()))
                .thenThrow(new RuntimeException("DB error"));
        var resp = controller.authenticateFinish(Map.of("email", "user@example.com", "response", "{}"));
        assertThat(resp.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void authenticateFinish_success_returnsJwt() {
        when(authInnerClient.passkeyAuthenticateFinish("user@example.com", "{}")).thenReturn("jwt-token");
        var resp = controller.authenticateFinish(Map.of("email", "user@example.com", "response", "{}"));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("token", "jwt-token");
    }
}
