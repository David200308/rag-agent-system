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
class GoogleSheetsServiceTest {

    @Mock ConnectorTokenRepository tokenRepo;
    @Mock RestClient.Builder       restClientBuilder;

    private final ConnectorProperties props = new ConnectorProperties(
            new ConnectorProperties.Google("g-client-id", "g-secret"),
            new ConnectorProperties.Figma("f-client-id",  "f-secret"),
            "https://app.example.com"
    );

    GoogleSheetsService service;

    @BeforeEach
    void setUp() {
        service = new GoogleSheetsService(tokenRepo, props, restClientBuilder);
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

    // ── createSpreadsheet — guard: not connected ──────────────────────────────

    @Test
    void createSpreadsheet_noToken_throwsIllegalState() {
        when(tokenRepo.findByOwnerEmailAndProvider("user@test.com", "google"))
                .thenReturn(Optional.empty());
        when(tokenRepo.findByOwnerEmailAndProvider("", "google"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.createSpreadsheet("My Sheet", "col1,col2\nval1,val2", "user@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    // ── readSpreadsheet — guard: not connected ────────────────────────────────

    @Test
    void readSpreadsheet_noToken_throwsIllegalState() {
        when(tokenRepo.findByOwnerEmailAndProvider("user@test.com", "google"))
                .thenReturn(Optional.empty());
        when(tokenRepo.findByOwnerEmailAndProvider("", "google"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.readSpreadsheet("https://docs.google.com/spreadsheets/d/abc123/edit", "user@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    // ── non-expiring token passes the guard ───────────────────────────────────

    @Test
    void createSpreadsheet_nonExpiringToken_passesThroughToHttp() {
        ConnectorToken token = ConnectorToken.builder()
                .ownerEmail("user@test.com")
                .provider("google")
                .accessToken("valid-token")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(tokenRepo.findByOwnerEmailAndProvider("user@test.com", "google"))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.createSpreadsheet("Title", "a,b", "user@test.com"))
                .isNotInstanceOf(IllegalStateException.class);
    }
}
