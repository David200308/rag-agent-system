package com.agentsystem.auth.controller;

import com.agentsystem.auth.AuthInnerClient;
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

    @Mock AuthInnerClient authInnerClient;
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
        doNothing().when(authInnerClient).requestLoginOtp("user@example.com");
        var resp = controller.requestOtp(Map.of("email", "user@example.com"));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("message", "Code sent to user@example.com");
    }

    @Test
    void requestOtp_notWhitelisted_returns403() {
        doThrow(new IllegalArgumentException("not authorised"))
                .when(authInnerClient).requestLoginOtp("unknown@example.com");
        var resp = controller.requestOtp(Map.of("email", "unknown@example.com"));
        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        assertThat(resp.getBody()).containsKey("error");
    }

    @Test
    void requestOtp_unexpectedException_returns500() {
        doThrow(new RuntimeException("auth-inner unreachable"))
                .when(authInnerClient).requestLoginOtp("user@example.com");
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
        when(authInnerClient.verifyLoginOtp("user@example.com", "123456", "PERSONAL", null))
                .thenReturn("signed-jwt");
        var resp = controller.verifyOtp(Map.of("email", "user@example.com", "code", "123456"));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("token", "signed-jwt");
    }

    @Test
    void verifyOtp_success_teamMode_returnsToken() {
        when(authInnerClient.verifyLoginOtp("user@example.com", "123456", "TEAM", "skyproton"))
                .thenReturn("team-jwt");
        var resp = controller.verifyOtp(Map.of(
                "email", "user@example.com", "code", "123456",
                "mode", "TEAM", "orgId", "skyproton"));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("token", "team-jwt");
    }

    @Test
    void verifyOtp_teamMode_notMember_returns401() {
        when(authInnerClient.verifyLoginOtp("user@example.com", "123456", "TEAM", "unknown-org"))
                .thenThrow(new IllegalArgumentException("not a member"));
        var resp = controller.verifyOtp(Map.of(
                "email", "user@example.com", "code", "123456",
                "mode", "TEAM", "orgId", "unknown-org"));
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void verifyOtp_invalidCode_returns401() {
        when(authInnerClient.verifyLoginOtp("user@example.com", "000000", "PERSONAL", null))
                .thenThrow(new IllegalArgumentException("Invalid or expired"));
        var resp = controller.verifyOtp(Map.of("email", "user@example.com", "code", "000000"));
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void verifyOtp_unexpectedException_returns500() {
        when(authInnerClient.verifyLoginOtp(anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("auth-inner unreachable"));
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
    void validate_validBearerToken_returnsValidTrueWithEmailAndMode() {
        when(authInnerClient.validate("my-token"))
                .thenReturn(new AuthInnerClient.ValidateResult(true, "uuid-1", "user@example.com", "PERSONAL", null));
        var resp = controller.validate("Bearer my-token");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("valid", true);
        assertThat(resp.getBody()).containsEntry("email", "user@example.com");
        assertThat(resp.getBody()).containsEntry("mode", "PERSONAL");
    }

    @Test
    void validate_teamToken_returnsOrgId() {
        when(authInnerClient.validate("team-token"))
                .thenReturn(new AuthInnerClient.ValidateResult(true, "uuid-1", "user@example.com", "TEAM", "skyproton"));
        var resp = controller.validate("Bearer team-token");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("mode", "TEAM");
        assertThat(resp.getBody()).containsEntry("orgId", "skyproton");
    }

    @Test
    void validate_expiredToken_returnsValidFalse() {
        when(authInnerClient.validate("expired-token"))
                .thenReturn(new AuthInnerClient.ValidateResult(false, null, null, null, null));
        var resp = controller.validate("Bearer expired-token");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("valid", false);
        assertThat(resp.getBody()).doesNotContainKey("email");
    }

    @Test
    void validate_noAuthHeader_returnsValidFalse() {
        when(authInnerClient.validate(null))
                .thenReturn(new AuthInnerClient.ValidateResult(false, null, null, null, null));
        var resp = controller.validate(null);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("valid", false);
    }

    @Test
    void validate_nonBearerHeader_returnsValidFalse() {
        when(authInnerClient.validate(null))
                .thenReturn(new AuthInnerClient.ValidateResult(false, null, null, null, null));
        var resp = controller.validate("Basic dXNlcjpwYXNz");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("valid", false);
    }

    // ── registerKey ────────────────────────────────────────────────────────────

    @Test
    void registerKey_noAuthHeader_returns401() {
        when(authInnerClient.validate(null))
                .thenReturn(new AuthInnerClient.ValidateResult(false, null, null, null, null));
        var resp = controller.registerKey(Map.of("publicKey", "abc"), null);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void registerKey_missingPublicKey_returns400() {
        when(authInnerClient.validate("my-token"))
                .thenReturn(new AuthInnerClient.ValidateResult(true, "uuid-1", "user@example.com", "PERSONAL", null));
        var resp = controller.registerKey(Map.of(), "Bearer my-token");
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void registerKey_success_returnsFingerprint() {
        when(authInnerClient.validate("my-token"))
                .thenReturn(new AuthInnerClient.ValidateResult(true, "uuid-1", "user@example.com", "PERSONAL", null));
        when(authInnerClient.registerCliKey("user@example.com", "abc")).thenReturn("AbCdEfGh");
        var resp = controller.registerKey(Map.of("publicKey", "abc"), "Bearer my-token");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("fingerprint", "AbCdEfGh");
    }

    @Test
    void registerKey_invalidKey_returns400() {
        when(authInnerClient.validate("my-token"))
                .thenReturn(new AuthInnerClient.ValidateResult(true, "uuid-1", "user@example.com", "PERSONAL", null));
        when(authInnerClient.registerCliKey("user@example.com", "bad"))
                .thenThrow(new IllegalArgumentException("Invalid Ed25519 public key"));
        var resp = controller.registerKey(Map.of("publicKey", "bad"), "Bearer my-token");
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ── checkOrg ───────────────────────────────────────────────────────────────

    @Test
    void checkOrg_existingOrg_returnsTrue() {
        when(authInnerClient.orgExists("skyproton")).thenReturn(true);
        var resp = controller.checkOrg("skyproton");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("exists", true);
    }

    @Test
    void checkOrg_unknownOrg_returnsFalse() {
        when(authInnerClient.orgExists("unknown")).thenReturn(false);
        var resp = controller.checkOrg("unknown");
        assertThat(resp.getBody()).containsEntry("exists", false);
    }
}
