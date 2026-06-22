package com.agentsystem.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentsystem.auth.service.PasskeyService;
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
class PasskeyUserControllerTest {

    @Mock PasskeyService    passkeyService;
    @Mock HttpServletRequest request;
    ObjectMapper objectMapper = new ObjectMapper();

    PasskeyUserController controller;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        controller = new PasskeyUserController(passkeyService, objectMapper);
    }

    private void stubEmail(String email) {
        when(request.getAttribute("authenticatedEmail")).thenReturn(email);
    }

    // ── registerBegin ─────────────────────────────────────────────────────────

    @Test
    void registerBegin_noEmail_returns401() {
        stubEmail(null);

        ResponseEntity<Object> resp = controller.registerBegin(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void registerBegin_success_returns200() throws Exception {
        stubEmail("user@test.com");
        when(passkeyService.startRegistration("user@test.com"))
                .thenReturn("{\"challenge\":\"abc123\"}");

        ResponseEntity<Object> resp = controller.registerBegin(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void registerBegin_serviceThrows_returns500() throws Exception {
        stubEmail("user@test.com");
        when(passkeyService.startRegistration(anyString()))
                .thenThrow(new RuntimeException("WebAuthn error"));

        ResponseEntity<Object> resp = controller.registerBegin(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(500);
    }

    // ── registerFinish ────────────────────────────────────────────────────────

    @Test
    void registerFinish_noEmail_returns401() {
        stubEmail(null);

        ResponseEntity<Map<String, String>> resp = controller.registerFinish(
                Map.of("response", "{\"id\":\"cred-1\"}"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void registerFinish_missingResponse_returns400() {
        stubEmail("user@test.com");

        ResponseEntity<Map<String, String>> resp = controller.registerFinish(Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).containsKey("error");
    }

    @Test
    void registerFinish_success_returns200() throws Exception {
        stubEmail("user@test.com");
        doNothing().when(passkeyService).finishRegistration(anyString(), anyString());

        ResponseEntity<Map<String, String>> resp = controller.registerFinish(
                Map.of("response", "{\"id\":\"cred-1\"}"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("message", "Passkey registered successfully");
    }

    @Test
    void registerFinish_illegalArgument_returns400() throws Exception {
        stubEmail("user@test.com");
        doThrow(new IllegalArgumentException("challenge expired"))
                .when(passkeyService).finishRegistration(anyString(), anyString());

        ResponseEntity<Map<String, String>> resp = controller.registerFinish(
                Map.of("response", "{\"id\":\"cred-1\"}"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).containsKey("error");
    }

    @Test
    void registerFinish_illegalState_returns400() throws Exception {
        stubEmail("user@test.com");
        doThrow(new IllegalStateException("credential already registered"))
                .when(passkeyService).finishRegistration(anyString(), anyString());

        ResponseEntity<Map<String, String>> resp = controller.registerFinish(
                Map.of("response", "{\"id\":\"cred-1\"}"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void registerFinish_unexpectedException_returns500() throws Exception {
        stubEmail("user@test.com");
        doThrow(new RuntimeException("internal error"))
                .when(passkeyService).finishRegistration(anyString(), anyString());

        ResponseEntity<Map<String, String>> resp = controller.registerFinish(
                Map.of("response", "{\"id\":\"cred-1\"}"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(500);
    }

    // ── deletePasskey ─────────────────────────────────────────────────────────

    @Test
    void deletePasskey_noEmail_returns401() {
        stubEmail(null);

        ResponseEntity<Map<String, String>> resp = controller.deletePasskey(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void deletePasskey_success_returns200() {
        stubEmail("user@test.com");
        doNothing().when(passkeyService).deletePasskeys("user@test.com");

        ResponseEntity<Map<String, String>> resp = controller.deletePasskey(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("message", "Passkey removed");
        verify(passkeyService).deletePasskeys("user@test.com");
    }
}
