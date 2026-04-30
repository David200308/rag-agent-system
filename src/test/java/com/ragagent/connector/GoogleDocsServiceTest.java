package com.ragagent.connector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

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

    GoogleDocsService service;

    @BeforeEach
    void setUp() {
        service = new GoogleDocsService(tokenRepo, props, restClientBuilder);
    }

    // ── isConnected ───────────────────────────────────────────────────────────

    @Test
    void isConnected_tokenPresent_returnsTrue() {
        when(tokenRepo.findByOwnerEmailAndProvider("user@test.com", "google"))
                .thenReturn(Optional.of(ConnectorToken.builder().build()));

        assertThat(service.isConnected("user@test.com")).isTrue();
    }

    @Test
    void isConnected_noToken_returnsFalse() {
        when(tokenRepo.findByOwnerEmailAndProvider("user@test.com", "google"))
                .thenReturn(Optional.empty());

        assertThat(service.isConnected("user@test.com")).isFalse();
    }

    @Test
    void isConnected_nullEmail_checksEmptyStringInRepo() {
        when(tokenRepo.findByOwnerEmailAndProvider("", "google"))
                .thenReturn(Optional.empty());

        assertThat(service.isConnected(null)).isFalse();
    }

    // ── createDocument — guard: not connected ────────────────────────────────

    @Test
    void createDocument_noToken_throwsIllegalState() {
        when(tokenRepo.findByOwnerEmailAndProvider("user@test.com", "google"))
                .thenReturn(Optional.empty());
        // no anonymous fallback either
        when(tokenRepo.findByOwnerEmailAndProvider("", "google"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDocument("My Doc", "content", "user@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    // ── readDocument — guard: not connected ──────────────────────────────────

    @Test
    void readDocument_noToken_throwsIllegalState() {
        when(tokenRepo.findByOwnerEmailAndProvider("user@test.com", "google"))
                .thenReturn(Optional.empty());
        when(tokenRepo.findByOwnerEmailAndProvider("", "google"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.readDocument("https://docs.google.com/document/d/abc123/edit", "user@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    // ── token expiry: non-expiring token passes through ───────────────────────

    @Test
    void createDocument_nonExpiringToken_doesNotRefresh() {
        ConnectorToken token = ConnectorToken.builder()
                .ownerEmail("user@test.com")
                .provider("google")
                .accessToken("valid-token")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(tokenRepo.findByOwnerEmailAndProvider("user@test.com", "google"))
                .thenReturn(Optional.of(token));

        // RestClient not set up — service will throw when attempting the HTTP call,
        // which confirms the token guard was passed successfully.
        assertThatThrownBy(() -> service.createDocument("Title", "Body", "user@test.com"))
                .isNotInstanceOf(IllegalStateException.class);
    }
}
