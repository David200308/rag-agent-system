package com.ragagent.connector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramServiceTest {

    private static final String BOT_TOKEN    = "test-bot-token";
    private static final String BOT_USERNAME = "test_bot";

    @Mock ConnectorTokenRepository tokenRepo;
    @Mock RestClient.Builder       restClientBuilder;

    private TelegramService service;

    @BeforeEach
    void setUp() {
        ConnectorProperties props = new ConnectorProperties(
                new ConnectorProperties.Google("g-id", "g-secret"),
                new ConnectorProperties.Figma("f-id", "f-secret"),
                new ConnectorProperties.Telegram(BOT_TOKEN, BOT_USERNAME),
                "https://app.example.com"
        );
        service = new TelegramService(props, tokenRepo, restClientBuilder);
    }

    // ── getBotUsername ────────────────────────────────────────────────────────

    @Test
    void getBotUsername_returnsConfiguredUsername() {
        assertThat(service.getBotUsername()).isEqualTo(BOT_USERNAME);
    }

    @Test
    void getBotUsername_nullTelegramConfig_returnsEmpty() {
        ConnectorProperties noTelegram = new ConnectorProperties(
                new ConnectorProperties.Google("g-id", "g-secret"),
                new ConnectorProperties.Figma("f-id", "f-secret"),
                null,
                "https://app.example.com"
        );
        TelegramService svc = new TelegramService(noTelegram, tokenRepo, restClientBuilder);
        assertThat(svc.getBotUsername()).isEmpty();
    }

    // ── validateAndConnect ────────────────────────────────────────────────────

    @Test
    void validateAndConnect_validPayload_savesToken() {
        Map<String, Object> authData = buildAuthData("123456", "John", Instant.now().getEpochSecond());

        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "telegram"))
                .thenReturn(Optional.empty());
        when(tokenRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.validateAndConnect(authData, "user@test.com", null);

        ArgumentCaptor<ConnectorToken> captor = ArgumentCaptor.forClass(ConnectorToken.class);
        verify(tokenRepo).save(captor.capture());
        assertThat(captor.getValue().getAccessToken()).isEqualTo("123456");
        assertThat(captor.getValue().getOwnerEmail()).isEqualTo("user@test.com");
        assertThat(captor.getValue().getProvider()).isEqualTo("telegram");
        assertThat(captor.getValue().getTokenType()).isEqualTo("telegram");
    }

    @Test
    void validateAndConnect_existingToken_updatesAccessToken() {
        Map<String, Object> authData = buildAuthData("777", null, Instant.now().getEpochSecond());

        ConnectorToken existing = ConnectorToken.builder()
                .ownerEmail("user@test.com").provider("telegram").accessToken("old-id").build();
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "telegram"))
                .thenReturn(Optional.of(existing));
        when(tokenRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.validateAndConnect(authData, "user@test.com", null);

        ArgumentCaptor<ConnectorToken> captor = ArgumentCaptor.forClass(ConnectorToken.class);
        verify(tokenRepo).save(captor.capture());
        assertThat(captor.getValue().getAccessToken()).isEqualTo("777");
    }

    @Test
    void validateAndConnect_nullEmail_normalizedToEmpty() {
        Map<String, Object> authData = buildAuthData("999", null, Instant.now().getEpochSecond());

        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("", "telegram")).thenReturn(Optional.empty());
        when(tokenRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.validateAndConnect(authData, null, null);

        ArgumentCaptor<ConnectorToken> captor = ArgumentCaptor.forClass(ConnectorToken.class);
        verify(tokenRepo).save(captor.capture());
        assertThat(captor.getValue().getOwnerEmail()).isEmpty();
    }

    @Test
    void validateAndConnect_missingHash_throws() {
        Map<String, Object> authData = Map.of("id", "123", "auth_date", "999999999");

        assertThatThrownBy(() -> service.validateAndConnect(authData, "user@test.com", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing hash");
    }

    @Test
    void validateAndConnect_wrongHash_throws() {
        Map<String, Object> authData = new HashMap<>();
        authData.put("id", "123456");
        authData.put("auth_date", String.valueOf(Instant.now().getEpochSecond()));
        authData.put("hash", "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

        assertThatThrownBy(() -> service.validateAndConnect(authData, "user@test.com", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hash validation failed");
    }

    @Test
    void validateAndConnect_staleAuthDate_throws() {
        long staleDate = Instant.now().getEpochSecond() - 90_000; // > 24 h
        Map<String, Object> authData = buildAuthData("123456", null, staleDate);

        assertThatThrownBy(() -> service.validateAndConnect(authData, "user@test.com", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    // ── isConnected ───────────────────────────────────────────────────────────

    @Test
    void isConnected_tokenPresent_returnsTrue() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "telegram"))
                .thenReturn(Optional.of(ConnectorToken.builder().build()));
        assertThat(service.isConnected("user@test.com", null)).isTrue();
    }

    @Test
    void isConnected_noToken_returnsFalse() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "telegram"))
                .thenReturn(Optional.empty());
        assertThat(service.isConnected("user@test.com", null)).isFalse();
    }

    @Test
    void isConnected_nullEmail_normalizedToEmpty() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("", "telegram")).thenReturn(Optional.empty());
        assertThat(service.isConnected(null, null)).isFalse();
        verify(tokenRepo).findByOwnerEmailAndProviderAndOrgIdIsNull("", "telegram");
    }

    // ── disconnect ────────────────────────────────────────────────────────────

    @Test
    void disconnect_deletesToken() {
        service.disconnect("user@test.com", null);
        verify(tokenRepo).deleteByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "telegram");
    }

    @Test
    void disconnect_nullEmail_normalizedToEmpty() {
        service.disconnect(null, null);
        verify(tokenRepo).deleteByOwnerEmailAndProviderAndOrgIdIsNull("", "telegram");
    }

    // ── sendMessage ───────────────────────────────────────────────────────────

    @Test
    void sendMessage_notConnected_throws() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "telegram"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendMessage("user@test.com", null, "hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> buildAuthData(String id, String firstName, long authDate) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        if (firstName != null) data.put("first_name", firstName);
        data.put("auth_date", String.valueOf(authDate));
        data.put("hash", computeValidHash(data));
        return data;
    }

    private String computeValidHash(Map<String, Object> authData) {
        String dataCheckString = authData.entrySet().stream()
                .filter(e -> !"hash".equals(e.getKey()) && e.getValue() != null)
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));
        try {
            byte[] key = MessageDigest.getInstance("SHA-256")
                    .digest(BOT_TOKEN.getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
