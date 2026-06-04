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
class GoogleSlidesServiceTest {

    @Mock ConnectorTokenRepository tokenRepo;
    @Mock RestClient.Builder       restClientBuilder;

    private final ConnectorProperties props = new ConnectorProperties(
            new ConnectorProperties.Google("g-client-id", "g-secret"),
            new ConnectorProperties.Figma("f-client-id",  "f-secret"),
            null,
            "https://app.example.com"
    );

    GoogleSlidesService service;

    @BeforeEach
    void setUp() {
        service = new GoogleSlidesService(tokenRepo, props, restClientBuilder);
    }

    // ── isConnected ───────────────────────────────────────────────────────────

    @Test
    void isConnected_tokenPresent_returnsTrue() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.of(ConnectorToken.builder().build()));

        assertThat(service.isConnected("user@test.com", null)).isTrue();
    }

    @Test
    void isConnected_noToken_returnsFalse() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.empty());

        assertThat(service.isConnected("user@test.com", null)).isFalse();
    }

    @Test
    void isConnected_nullEmail_checksEmptyStringInRepo() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("", "google"))
                .thenReturn(Optional.empty());

        assertThat(service.isConnected(null, null)).isFalse();
    }

    // ── createPresentation — guard: not connected ─────────────────────────────

    @Test
    void createPresentation_noToken_throwsIllegalState() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.createPresentation("My Deck", "Slide 1\nbody text\n---\nSlide 2", "user@test.com", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    // ── readPresentation — guard: not connected ───────────────────────────────

    @Test
    void readPresentation_noToken_throwsIllegalState() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.readPresentation("https://docs.google.com/presentation/d/abc123/edit", "user@test.com", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    // ── non-expiring token passes the guard ───────────────────────────────────

    @Test
    void createPresentation_nonExpiringToken_passesThroughToHttp() {
        ConnectorToken token = ConnectorToken.builder()
                .ownerEmail("user@test.com")
                .provider("google")
                .accessToken("valid-token")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.createPresentation("Title", "Slide 1", "user@test.com", null))
                .isNotInstanceOf(IllegalStateException.class);
    }
}
