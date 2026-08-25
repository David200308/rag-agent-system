package com.agentsystem.connector.service;

import com.agentsystem.connector.service.impl.GoogleCalendarServiceImpl;

import com.agentsystem.connector.ConnectorProperties;
import com.agentsystem.connector.entity.ConnectorToken;
import com.agentsystem.connector.repository.ConnectorTokenRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarServiceTest {

    @Mock ConnectorTokenRepository tokenRepo;
    @Mock RestClient.Builder       restClientBuilder;

    private final ConnectorProperties props = new ConnectorProperties(
            new ConnectorProperties.Google("g-client-id", "g-secret"),
            new ConnectorProperties.Figma("f-client-id",  "f-secret"),
            null,
            "https://app.example.com"
    );

    GoogleCalendarServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GoogleCalendarServiceImpl(tokenRepo, props, restClientBuilder);
    }

    // ── isConnected ───────────────────────────────────────────────────────────

    @Test
    void isConnected_tokenPresent_returnsTrue() {
        when(tokenRepo.findByOwnerUuidAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.of(ConnectorToken.builder().build()));

        assertThat(service.isConnected("user@test.com", null)).isTrue();
    }

    @Test
    void isConnected_noToken_returnsFalse() {
        when(tokenRepo.findByOwnerUuidAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.empty());

        assertThat(service.isConnected("user@test.com", null)).isFalse();
    }

    @Test
    void isConnected_nullEmail_checksEmptyStringInRepo() {
        when(tokenRepo.findByOwnerUuidAndProviderAndOrgIdIsNull("", "google"))
                .thenReturn(Optional.empty());

        assertThat(service.isConnected(null, null)).isFalse();
    }

    @Test
    void isConnected_withOrgId_usesOrgScopedRepo() {
        when(tokenRepo.findByOwnerUuidAndProviderAndOrgId("user@test.com", "google", "org-1"))
                .thenReturn(Optional.of(ConnectorToken.builder().build()));

        assertThat(service.isConnected("user@test.com", "org-1")).isTrue();
    }

    @Test
    void isConnected_withOrgId_noToken_returnsFalse() {
        when(tokenRepo.findByOwnerUuidAndProviderAndOrgId("user@test.com", "google", "org-1"))
                .thenReturn(Optional.empty());

        assertThat(service.isConnected("user@test.com", "org-1")).isFalse();
    }

    // ── listEvents — guard: not connected ────────────────────────────────────

    @Test
    void listEvents_noToken_throwsIllegalState() {
        when(tokenRepo.findByOwnerUuidAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listEvents("user@test.com", null, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    // ── createEvent — guard: not connected ───────────────────────────────────

    @Test
    void createEvent_noToken_throwsIllegalState() {
        when(tokenRepo.findByOwnerUuidAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.createEvent("user@test.com", null, "Meeting",
                        "2025-06-10T14:00:00Z", "2025-06-10T15:00:00Z", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    // ── non-expiring token passes the guard ───────────────────────────────────

    @Test
    void listEvents_nonExpiringToken_attemptsHttpCall() {
        ConnectorToken token = ConnectorToken.builder()
                .ownerUuid("user@test.com")
                .provider("google")
                .accessToken("valid-token")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(tokenRepo.findByOwnerUuidAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.of(token));

        // RestClient not set up — service will throw when attempting the HTTP call,
        // which confirms the token guard was passed successfully.
        assertThatThrownBy(() -> service.listEvents("user@test.com", null, 10))
                .isNotInstanceOf(IllegalStateException.class);
    }

    @Test
    void createEvent_nonExpiringToken_attemptsHttpCall() {
        ConnectorToken token = ConnectorToken.builder()
                .ownerUuid("user@test.com")
                .provider("google")
                .accessToken("valid-token")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(tokenRepo.findByOwnerUuidAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() ->
                service.createEvent("user@test.com", null, "Meeting",
                        "2025-06-10T14:00:00Z", "2025-06-10T15:00:00Z", "Discuss", "Office"))
                .isNotInstanceOf(IllegalStateException.class);
    }

    // ── isExpiringSoon (via reflection) ───────────────────────────────────────

    @Test
    void isExpiringSoon_tokenExpiresInOneHour_returnsFalse() throws Exception {
        ConnectorToken ct = ConnectorToken.builder()
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        assertThat(callIsExpiringSoon(ct)).isFalse();
    }

    @Test
    void isExpiringSoon_tokenAlreadyExpired_returnsTrue() throws Exception {
        ConnectorToken ct = ConnectorToken.builder()
                .expiresAt(LocalDateTime.now().minusMinutes(5))
                .build();
        assertThat(callIsExpiringSoon(ct)).isTrue();
    }

    @Test
    void isExpiringSoon_tokenExpiresSoon_returnsTrue() throws Exception {
        ConnectorToken ct = ConnectorToken.builder()
                .expiresAt(LocalDateTime.now().plusSeconds(100))
                .build();
        assertThat(callIsExpiringSoon(ct)).isTrue();
    }

    @Test
    void isExpiringSoon_nullExpiresAt_returnsFalse() throws Exception {
        ConnectorToken ct = ConnectorToken.builder().build();
        assertThat(callIsExpiringSoon(ct)).isFalse();
    }

    // ── refreshToken — no refresh token returns original ─────────────────────

    @Test
    void refreshToken_noRefreshToken_returnsOriginalToken() throws Exception {
        ConnectorToken ct = ConnectorToken.builder()
                .accessToken("access-token")
                .build();

        ConnectorToken result = callRefreshToken(ct);

        assertThat(result).isSameAs(ct);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean callIsExpiringSoon(ConnectorToken ct) throws Exception {
        Method m = GoogleCalendarServiceImpl.class.getDeclaredMethod("isExpiringSoon", ConnectorToken.class);
        m.setAccessible(true);
        return (boolean) m.invoke(service, ct);
    }

    private ConnectorToken callRefreshToken(ConnectorToken ct) throws Exception {
        Method m = GoogleCalendarServiceImpl.class.getDeclaredMethod("refreshToken", ConnectorToken.class);
        m.setAccessible(true);
        return (ConnectorToken) m.invoke(service, ct);
    }
}
