package com.agentsystem.connector.service;

import com.agentsystem.connector.service.impl.GoogleDocsServiceImpl;

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
class GoogleDocsServiceTest {

    @Mock ConnectorTokenRepository tokenRepo;
    @Mock RestClient.Builder       restClientBuilder;

    private final ConnectorProperties props = new ConnectorProperties(
            new ConnectorProperties.Google("g-client-id", "g-secret"),
            new ConnectorProperties.Figma("f-client-id",  "f-secret"),
            null,
            "https://app.example.com"
    );

    GoogleDocsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GoogleDocsServiceImpl(tokenRepo, props, restClientBuilder);
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

    // ── createDocument — guard: not connected ────────────────────────────────

    @Test
    void createDocument_noToken_throwsIllegalState() {
        when(tokenRepo.findByOwnerUuidAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDocument("My Doc", "content", "user@test.com", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    // ── readDocument — guard: not connected ──────────────────────────────────

    @Test
    void readDocument_noToken_throwsIllegalState() {
        when(tokenRepo.findByOwnerUuidAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.readDocument("https://docs.google.com/document/d/abc123/edit", "user@test.com", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    // ── token expiry: non-expiring token passes through ───────────────────────

    @Test
    void createDocument_nonExpiringToken_doesNotRefresh() {
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
        assertThatThrownBy(() -> service.createDocument("Title", "Body", "user@test.com", null))
                .isNotInstanceOf(IllegalStateException.class);
    }

    // ── isConnected with orgId ────────────────────────────────────────────────

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

    // ── extractDocId via reflection ───────────────────────────────────────────

    @Test
    void extractDocId_fullUrl_extractsId() throws Exception {
        String id = callExtractDocId("https://docs.google.com/document/d/abc123xyz/edit");
        assertThat(id).isEqualTo("abc123xyz");
    }

    @Test
    void extractDocId_rawId_returnsAsIs() throws Exception {
        String id = callExtractDocId("abc123xyz");
        assertThat(id).isEqualTo("abc123xyz");
    }

    @Test
    void extractDocId_urlWithQueryParams_extractsCorrectly() throws Exception {
        String id = callExtractDocId(
                "https://docs.google.com/document/d/1ABC-xyz_789/edit?usp=sharing");
        assertThat(id).isEqualTo("1ABC-xyz_789");
    }

    @Test
    void extractDocId_bareIdWithSpaces_trimmed() throws Exception {
        String id = callExtractDocId("  docId123  ");
        assertThat(id).isEqualTo("docId123");
    }

    // ── isExpiringSoon via reflection ─────────────────────────────────────────

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
    void isExpiringSoon_tokenExpiresIn100Seconds_returnsTrue() throws Exception {
        ConnectorToken ct = ConnectorToken.builder()
                .expiresAt(LocalDateTime.now().plusSeconds(100))
                .build();
        assertThat(callIsExpiringSoon(ct)).isTrue();
    }

    @Test
    void isExpiringSoon_nullExpiresAt_returnsFalse() throws Exception {
        ConnectorToken ct = ConnectorToken.builder().expiresAt(null).build();
        assertThat(callIsExpiringSoon(ct)).isFalse();
    }

    // ── Reflection helpers ────────────────────────────────────────────────────

    private String callExtractDocId(String input) throws Exception {
        Method m = GoogleDocsServiceImpl.class.getDeclaredMethod("extractDocId", String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, input);
    }

    private boolean callIsExpiringSoon(ConnectorToken ct) throws Exception {
        Method m = GoogleDocsServiceImpl.class.getDeclaredMethod("isExpiringSoon", ConnectorToken.class);
        m.setAccessible(true);
        return (boolean) m.invoke(service, ct);
    }
}
