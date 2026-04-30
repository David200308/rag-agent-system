package com.ragagent.connector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectorServiceTest {

    @Mock ConnectorTokenRepository      tokenRepo;
    @Mock ConnectorOAuthStateRepository stateRepo;
    @Mock RestClient.Builder            restClientBuilder;

    private final ConnectorProperties props = new ConnectorProperties(
            new ConnectorProperties.Google("g-client-id", "g-client-secret"),
            new ConnectorProperties.Figma("f-client-id",  "f-client-secret"),
            "https://app.example.com"
    );

    ConnectorService service;

    @BeforeEach
    void setUp() {
        service = new ConnectorService(props, tokenRepo, stateRepo, restClientBuilder);
    }

    // ── getStatus ─────────────────────────────────────────────────────────────

    @Test
    void getStatus_noTokens_returnsFalseForAll() {
        when(tokenRepo.findByOwnerEmail("user@test.com")).thenReturn(List.of());

        Map<String, Boolean> status = service.getStatus("user@test.com");

        assertThat(status.get("google")).isFalse();
        assertThat(status.get("figma")).isFalse();
    }

    @Test
    void getStatus_googleConnected_returnsGoogleTrue() {
        ConnectorToken token = ConnectorToken.builder()
                .ownerEmail("user@test.com").provider("google").accessToken("tok").build();
        when(tokenRepo.findByOwnerEmail("user@test.com")).thenReturn(List.of(token));

        Map<String, Boolean> status = service.getStatus("user@test.com");

        assertThat(status.get("google")).isTrue();
        assertThat(status.get("figma")).isFalse();
    }

    @Test
    void getStatus_nullEmail_normalizedToEmptyString() {
        when(tokenRepo.findByOwnerEmail("")).thenReturn(List.of());

        service.getStatus(null);

        verify(tokenRepo).findByOwnerEmail("");
    }

    // ── getToken ──────────────────────────────────────────────────────────────

    @Test
    void getToken_delegatesToRepo() {
        ConnectorToken token = ConnectorToken.builder()
                .ownerEmail("user@test.com").provider("google").build();
        when(tokenRepo.findByOwnerEmailAndProvider("user@test.com", "google"))
                .thenReturn(Optional.of(token));

        assertThat(service.getToken("google", "user@test.com")).contains(token);
    }

    @Test
    void getToken_nullEmail_normalizedToEmptyString() {
        when(tokenRepo.findByOwnerEmailAndProvider("", "figma")).thenReturn(Optional.empty());

        assertThat(service.getToken("figma", null)).isEmpty();

        verify(tokenRepo).findByOwnerEmailAndProvider("", "figma");
    }

    // ── disconnect ────────────────────────────────────────────────────────────

    @Test
    void disconnect_nonEmptyEmail_deletesUserAndAnonymousTokens() {
        service.disconnect("google", "user@test.com");

        verify(tokenRepo).deleteByOwnerEmailAndProvider("user@test.com", "google");
        verify(tokenRepo).deleteByOwnerEmailAndProvider("", "google");
    }

    @Test
    void disconnect_nullEmail_onlyDeletesEmptyEmailRecord() {
        service.disconnect("figma", null);

        verify(tokenRepo).deleteByOwnerEmailAndProvider("", "figma");
        verify(tokenRepo, never()).deleteByOwnerEmailAndProvider(argThat(e -> !e.isEmpty()), any());
    }

    @Test
    void disconnect_unknownProvider_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.disconnect("twitter", "user@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown provider");
    }

    // ── getAuthUrl ────────────────────────────────────────────────────────────

    @Test
    void getAuthUrl_google_containsExpectedParams() {
        when(stateRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String url = service.getAuthUrl("google", "user@test.com");

        assertThat(url).contains("accounts.google.com");
        assertThat(url).contains("client_id=g-client-id");
        assertThat(url).contains("redirect_uri=");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("access_type=offline");
    }

    @Test
    void getAuthUrl_figma_containsExpectedParams() {
        when(stateRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String url = service.getAuthUrl("figma", "user@test.com");

        assertThat(url).contains("figma.com/oauth");
        assertThat(url).contains("client_id=f-client-id");
    }

    @Test
    void getAuthUrl_savesOAuthStateToRepo() {
        when(stateRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<ConnectorOAuthState> captor =
                ArgumentCaptor.forClass(ConnectorOAuthState.class);

        service.getAuthUrl("google", "user@test.com");

        verify(stateRepo).save(captor.capture());
        ConnectorOAuthState saved = captor.getValue();
        assertThat(saved.getOwnerEmail()).isEqualTo("user@test.com");
        assertThat(saved.getProvider()).isEqualTo("google");
        assertThat(saved.getState()).isNotBlank();
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void getAuthUrl_unknownProvider_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getAuthUrl("slack", "user@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown provider");
    }

    // ── exchangeCode error paths (before HTTP call) ───────────────────────────

    @Test
    void exchangeCode_invalidState_throwsIllegalArgument() {
        when(stateRepo.findByState("bad-state")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exchangeCode("google", "code-abc", "bad-state"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid OAuth state");
    }

    @Test
    void exchangeCode_expiredState_throwsIllegalArgument() {
        ConnectorOAuthState expired = ConnectorOAuthState.builder()
                .state("s1")
                .ownerEmail("user@test.com")
                .provider("google")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(stateRepo.findByState("s1")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.exchangeCode("google", "code-abc", "s1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OAuth state expired");
    }

    @Test
    void exchangeCode_providerMismatch_throwsIllegalArgument() {
        ConnectorOAuthState state = ConnectorOAuthState.builder()
                .state("s2")
                .ownerEmail("user@test.com")
                .provider("figma")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        when(stateRepo.findByState("s2")).thenReturn(Optional.of(state));

        assertThatThrownBy(() -> service.exchangeCode("google", "code-abc", "s2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider mismatch");
    }
}
