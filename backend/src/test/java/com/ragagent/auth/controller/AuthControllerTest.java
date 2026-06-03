package com.ragagent.auth.controller;

import com.ragagent.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock  AuthService    authService;
    @InjectMocks AuthController controller;

    // ── requestOtp ─────────────────────────────────────────────────────────────

    @Test
    void requestOtp_missingEmail_returns400() {
        var resp = controller.requestOtp(Map.of());
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).containsKey("error");
    }

    @Test
    void requestOtp_blankEmail_returns400() {
        var resp = controller.requestOtp(Map.of("email", "  "));
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void requestOtp_success_returns200WithMessage() {
        doNothing().when(authService).requestOtp("user@example.com");
        var resp = controller.requestOtp(Map.of("email", "user@example.com"));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("message", "Code sent to user@example.com");
    }

    @Test
    void requestOtp_notWhitelisted_returns403() {
        doThrow(new IllegalArgumentException("not authorised"))
                .when(authService).requestOtp("unknown@example.com");
        var resp = controller.requestOtp(Map.of("email", "unknown@example.com"));
        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        assertThat(resp.getBody()).containsKey("error");
    }

    @Test
    void requestOtp_unexpectedException_returns500() {
        doThrow(new RuntimeException("SMTP failure"))
                .when(authService).requestOtp("user@example.com");
        var resp = controller.requestOtp(Map.of("email", "user@example.com"));
        assertThat(resp.getStatusCode().value()).isEqualTo(500);
    }

    // ── verifyOtp ──────────────────────────────────────────────────────────────

    @Test
    void verifyOtp_missingCode_returns400() {
        var resp = controller.verifyOtp(Map.of("email", "user@example.com"));
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void verifyOtp_blankEmail_returns400() {
        var resp = controller.verifyOtp(Map.of("email", "", "code", "123456"));
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void verifyOtp_success_personalMode_returnsToken() {
        when(authService.verifyOtp("user@example.com", "123456", "PERSONAL", null))
                .thenReturn("signed-jwt");
        var resp = controller.verifyOtp(Map.of("email", "user@example.com", "code", "123456"));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("token", "signed-jwt");
    }

    @Test
    void verifyOtp_success_teamMode_returnsToken() {
        when(authService.verifyOtp("user@example.com", "123456", "TEAM", "skyproton"))
                .thenReturn("team-jwt");
        var resp = controller.verifyOtp(Map.of(
                "email", "user@example.com", "code", "123456",
                "mode", "TEAM", "orgId", "skyproton"));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("token", "team-jwt");
    }

    @Test
    void verifyOtp_teamMode_notMember_returns401() {
        when(authService.verifyOtp("user@example.com", "123456", "TEAM", "unknown-org"))
                .thenThrow(new IllegalArgumentException("not a member"));
        var resp = controller.verifyOtp(Map.of(
                "email", "user@example.com", "code", "123456",
                "mode", "TEAM", "orgId", "unknown-org"));
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void verifyOtp_invalidCode_returns401() {
        when(authService.verifyOtp("user@example.com", "000000", "PERSONAL", null))
                .thenThrow(new IllegalArgumentException("Invalid or expired"));
        var resp = controller.verifyOtp(Map.of("email", "user@example.com", "code", "000000"));
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void verifyOtp_unexpectedException_returns500() {
        when(authService.verifyOtp(anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("DB error"));
        var resp = controller.verifyOtp(Map.of("email", "user@example.com", "code", "123456"));
        assertThat(resp.getStatusCode().value()).isEqualTo(500);
    }

    // ── logout ─────────────────────────────────────────────────────────────────

    @Test
    void logout_alwaysReturns200WithMessage() {
        var resp = controller.logout();
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("message", "Logged out");
    }

    // ── validate ───────────────────────────────────────────────────────────────

    @Test
    void validate_validBearerToken_returnsValidTrueWithEmail() {
        when(authService.validateToken("my-token")).thenReturn("user@example.com");
        var resp = controller.validate("Bearer my-token");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("valid", true);
        assertThat(resp.getBody()).containsEntry("email", "user@example.com");
    }

    @Test
    void validate_expiredToken_returnsValidFalse() {
        when(authService.validateToken("expired-token")).thenReturn(null);
        var resp = controller.validate("Bearer expired-token");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("valid", false);
        assertThat(resp.getBody()).doesNotContainKey("email");
    }

    @Test
    void validate_noAuthHeader_returnsValidFalse() {
        var resp = controller.validate(null);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("valid", false);
        verifyNoInteractions(authService);
    }

    @Test
    void validate_nonBearerHeader_returnsValidFalse() {
        var resp = controller.validate("Basic dXNlcjpwYXNz");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("valid", false);
        verifyNoInteractions(authService);
    }
}
