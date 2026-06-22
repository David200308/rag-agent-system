package com.agentsystem.connector.service.impl;

import com.agentsystem.connector.service.ConnectorService;
import com.agentsystem.connector.service.TelegramService;

import com.agentsystem.connector.ConnectorProperties;
import com.agentsystem.connector.entity.ConnectorOAuthState;
import com.agentsystem.connector.entity.ConnectorToken;
import com.agentsystem.connector.repository.ConnectorOAuthStateRepository;
import com.agentsystem.connector.repository.ConnectorTokenRepository;

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
    @Mock TelegramService               telegramService;

    private final ConnectorProperties props = new ConnectorProperties(
            new ConnectorProperties.Google("g-client-id", "g-client-secret"),
            new ConnectorProperties.Figma("f-client-id",  "f-client-secret"),
            null,
            "https://app.example.com"
    );

    ConnectorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ConnectorServiceImpl(props, tokenRepo, stateRepo, restClientBuilder, telegramService);
    }

    // ── getStatus ─────────────────────────────────────────────────────────────

    @Test
    void getStatus_noTokens_returnsFalseForAll() {
        when(tokenRepo.findByOwnerEmailAndOrgIdIsNull("user@test.com")).thenReturn(List.of());
        when(telegramService.isConnected("user@test.com", null)).thenReturn(false);

        Map<String, Boolean> status = service.getStatus("user@test.com", null);

        assertThat(status.get("google")).isFalse();
        assertThat(status.get("figma")).isFalse();
        assertThat(status.get("telegram")).isFalse();
    }

    @Test
    void getStatus_googleConnected_returnsGoogleTrue() {
        ConnectorToken token = ConnectorToken.builder()
                .ownerEmail("user@test.com").provider("google").accessToken("tok").build();
        when(tokenRepo.findByOwnerEmailAndOrgIdIsNull("user@test.com")).thenReturn(List.of(token));
        when(telegramService.isConnected("user@test.com", null)).thenReturn(false);

        Map<String, Boolean> status = service.getStatus("user@test.com", null);

        assertThat(status.get("google")).isTrue();
        assertThat(status.get("figma")).isFalse();
        assertThat(status.get("telegram")).isFalse();
    }

    @Test
    void getStatus_telegramConnected_returnsTelegramTrue() {
        when(tokenRepo.findByOwnerEmailAndOrgIdIsNull("user@test.com")).thenReturn(List.of());
        when(telegramService.isConnected("user@test.com", null)).thenReturn(true);

        Map<String, Boolean> status = service.getStatus("user@test.com", null);

        assertThat(status.get("telegram")).isTrue();
        assertThat(status.get("google")).isFalse();
    }

    @Test
    void getStatus_nullEmail_normalizedToEmptyString() {
        when(tokenRepo.findByOwnerEmailAndOrgIdIsNull("")).thenReturn(List.of());
        when(telegramService.isConnected("", null)).thenReturn(false);

        service.getStatus(null, null);

        verify(tokenRepo).findByOwnerEmailAndOrgIdIsNull("");
    }

    // ── getToken ──────────────────────────────────────────────────────────────

    @Test
    void getToken_delegatesToRepo() {
        ConnectorToken token = ConnectorToken.builder()
                .ownerEmail("user@test.com").provider("google").build();
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.of(token));

        assertThat(service.getToken("google", "user@test.com", null)).contains(token);
    }

    @Test
    void getToken_nullEmail_normalizedToEmptyString() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("", "figma")).thenReturn(Optional.empty());

        assertThat(service.getToken("figma", null, null)).isEmpty();

        verify(tokenRepo).findByOwnerEmailAndProviderAndOrgIdIsNull("", "figma");
    }

    // ── disconnect ────────────────────────────────────────────────────────────

    @Test
    void disconnect_nonEmptyEmail_deletesUserAndAnonymousTokens() {
        service.disconnect("google", "user@test.com", null);

        verify(tokenRepo).deleteByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "google");
        verify(tokenRepo).deleteByOwnerEmailAndProviderAndOrgIdIsNull("", "google");
    }

    @Test
    void disconnect_nullEmail_onlyDeletesEmptyEmailRecord() {
        service.disconnect("figma", null, null);

        verify(tokenRepo).deleteByOwnerEmailAndProviderAndOrgIdIsNull("", "figma");
        verify(tokenRepo, never()).deleteByOwnerEmailAndProviderAndOrgIdIsNull(argThat(e -> !e.isEmpty()), any());
    }

    @Test
    void disconnect_telegram_delegatesToTelegramService() {
        service.disconnect("telegram", "user@test.com", null);

        verify(telegramService).disconnect("user@test.com", null);
        verify(tokenRepo, never()).deleteByOwnerEmailAndProviderAndOrgIdIsNull(any(), any());
    }

    @Test
    void disconnect_unknownProvider_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.disconnect("twitter", "user@test.com", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown provider");
    }

    // ── getAuthUrl ────────────────────────────────────────────────────────────

    @Test
    void getAuthUrl_google_containsExpectedParams() {
        when(stateRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String url = service.getAuthUrl("google", "user@test.com", null);

        assertThat(url).contains("accounts.google.com");
        assertThat(url).contains("client_id=g-client-id");
        assertThat(url).contains("redirect_uri=");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("access_type=offline");
    }

    @Test
    void getAuthUrl_figma_containsExpectedParams() {
        when(stateRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String url = service.getAuthUrl("figma", "user@test.com", null);

        assertThat(url).contains("figma.com/oauth");
        assertThat(url).contains("client_id=f-client-id");
    }

    @Test
    void getAuthUrl_savesOAuthStateToRepo() {
        when(stateRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<ConnectorOAuthState> captor =
                ArgumentCaptor.forClass(ConnectorOAuthState.class);

        service.getAuthUrl("google", "user@test.com", null);

        verify(stateRepo).save(captor.capture());
        ConnectorOAuthState saved = captor.getValue();
        assertThat(saved.getOwnerEmail()).isEqualTo("user@test.com");
        assertThat(saved.getProvider()).isEqualTo("google");
        assertThat(saved.getState()).isNotBlank();
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void getAuthUrl_telegram_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getAuthUrl("telegram", "user@test.com", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Login Widget");
    }

    @Test
    void getAuthUrl_unknownProvider_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getAuthUrl("slack", "user@test.com", null))
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

    // ── getStatus — figma ─────────────────────────────────────────────────────

    @Test
    void getStatus_figmaConnected_returnsFigmaTrue() {
        ConnectorToken token = ConnectorToken.builder()
                .ownerEmail("user@test.com").provider("figma").accessToken("tok").build();
        when(tokenRepo.findByOwnerEmailAndOrgIdIsNull("user@test.com")).thenReturn(List.of(token));
        when(telegramService.isConnected("user@test.com", null)).thenReturn(false);

        Map<String, Boolean> status = service.getStatus("user@test.com", null);

        assertThat(status.get("figma")).isTrue();
        assertThat(status.get("google")).isFalse();
        assertThat(status.get("telegram")).isFalse();
    }

    @Test
    void getStatus_allConnected_returnsAllTrue() {
        ConnectorToken google = ConnectorToken.builder()
                .ownerEmail("user@test.com").provider("google").accessToken("g-tok").build();
        ConnectorToken figma = ConnectorToken.builder()
                .ownerEmail("user@test.com").provider("figma").accessToken("f-tok").build();
        when(tokenRepo.findByOwnerEmailAndOrgIdIsNull("user@test.com")).thenReturn(List.of(google, figma));
        when(telegramService.isConnected("user@test.com", null)).thenReturn(true);

        Map<String, Boolean> status = service.getStatus("user@test.com", null);

        assertThat(status.get("google")).isTrue();
        assertThat(status.get("figma")).isTrue();
        assertThat(status.get("telegram")).isTrue();
    }

    // ── disconnect — additional branches ──────────────────────────────────────

    @Test
    void disconnect_figma_deletesUserAndAnonymousTokens() {
        service.disconnect("figma", "user@test.com", null);

        verify(tokenRepo).deleteByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "figma");
        verify(tokenRepo).deleteByOwnerEmailAndProviderAndOrgIdIsNull("", "figma");
    }

    // ── getAuthUrl — scope / prompt params ────────────────────────────────────

    @Test
    void getAuthUrl_google_includesConsentPromptAndOfflineAccess() {
        when(stateRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String url = service.getAuthUrl("google", "user@test.com", null);

        assertThat(url).contains("prompt=consent");
        assertThat(url).contains("access_type=offline");
    }

    @Test
    void getAuthUrl_figma_includesFileReadScope() {
        when(stateRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String url = service.getAuthUrl("figma", "user@test.com", null);

        assertThat(url).contains("scope=file_read");
    }

    // ── purgeExpiredStates ────────────────────────────────────────────────────

    @Test
    void purgeExpiredStates_callsDeleteExpiredOnRepo() {
        service.purgeExpiredStates();

        verify(stateRepo).deleteExpired(any(LocalDateTime.class));
    }

    // ── exchangeCode — deleted state on expiry ─────────────────────────────────

    @Test
    void exchangeCode_expiredState_deletesStateBeforeThrowingException() {
        ConnectorOAuthState expired = ConnectorOAuthState.builder()
                .state("exp-state")
                .ownerEmail("user@test.com")
                .provider("google")
                .expiresAt(LocalDateTime.now().minusMinutes(5))
                .build();
        when(stateRepo.findByState("exp-state")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.exchangeCode("google", "code", "exp-state"))
                .isInstanceOf(IllegalArgumentException.class);

        // State must be deleted even when expired
        verify(stateRepo).delete(expired);
    }

    // ── getStatus with orgId ──────────────────────────────────────────────────

    @Test
    void getStatus_withOrgId_usesOrgScopedRepo() {
        ConnectorToken token = ConnectorToken.builder()
                .ownerEmail("user@test.com").provider("google").accessToken("tok").build();
        when(tokenRepo.findByOwnerEmailAndOrgId("user@test.com", "org-1")).thenReturn(List.of(token));
        when(telegramService.isConnected("user@test.com", "org-1")).thenReturn(false);

        Map<String, Boolean> status = service.getStatus("user@test.com", "org-1");

        assertThat(status.get("google")).isTrue();
        verify(tokenRepo).findByOwnerEmailAndOrgId("user@test.com", "org-1");
        verify(tokenRepo, never()).findByOwnerEmailAndOrgIdIsNull(anyString());
    }

    // ── getToken with orgId ───────────────────────────────────────────────────

    @Test
    void getToken_withOrgId_usesOrgScopedRepo() {
        ConnectorToken token = ConnectorToken.builder()
                .ownerEmail("user@test.com").provider("google").orgId("org-1").build();
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgId("user@test.com", "google", "org-1"))
                .thenReturn(Optional.of(token));

        assertThat(service.getToken("google", "user@test.com", "org-1")).contains(token);
        verify(tokenRepo).findByOwnerEmailAndProviderAndOrgId("user@test.com", "google", "org-1");
    }

    // ── disconnect with orgId ─────────────────────────────────────────────────

    @Test
    void disconnect_withOrgId_callsOrgScopedDelete() {
        service.disconnect("google", "user@test.com", "org-1");

        verify(tokenRepo).deleteByOwnerEmailAndProviderAndOrgId("user@test.com", "google", "org-1");
        verify(tokenRepo, never()).deleteByOwnerEmailAndProviderAndOrgIdIsNull(anyString(), anyString());
    }

    @Test
    void disconnect_telegram_withOrgId_delegatesToTelegramService() {
        service.disconnect("telegram", "user@test.com", "org-1");

        verify(telegramService).disconnect("user@test.com", "org-1");
        verify(tokenRepo, never()).deleteByOwnerEmailAndProviderAndOrgId(any(), any(), any());
    }

    // ── getAuthUrl with orgId ─────────────────────────────────────────────────

    @Test
    void getAuthUrl_withOrgId_savesOrgIdInState() {
        when(stateRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<ConnectorOAuthState> captor =
                ArgumentCaptor.forClass(ConnectorOAuthState.class);

        service.getAuthUrl("google", "user@test.com", "org-1");

        verify(stateRepo).save(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo("org-1");
    }
}
