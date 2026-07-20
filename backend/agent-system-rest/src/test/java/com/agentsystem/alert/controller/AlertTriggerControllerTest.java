package com.agentsystem.alert.controller;

import com.agentsystem.config.AlertProperties;
import com.agentsystem.connector.service.TelegramService;
import com.agentsystem.notification.NotificationClient;
import com.agentsystem.user.service.UserAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertTriggerControllerTest {

    @Mock NotificationClient  notificationClient;
    @Mock TelegramService     telegramService;
    @Mock UserAccountService  userAccountService;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    AlertProperties alertProperties = new AlertProperties("secret-key", "http://alert-task");

    AlertTriggerController controller;

    @BeforeEach
    void setUp() {
        Set<String> claimed = ConcurrentHashMap.newKeySet();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(inv -> claimed.add(inv.getArgument(0)));

        controller = new AlertTriggerController(
                alertProperties, notificationClient, telegramService, userAccountService, redisTemplate);
    }

    private static AlertTriggerController.AlertTriggerRequest body() {
        return new AlertTriggerController.AlertTriggerRequest(
                "owner-1", "org-1", "price", "rule-1", "BTC/USD", "BTC/USD crossed threshold");
    }

    // ── service key validation ────────────────────────────────────────────────

    @Test
    void trigger_nullServiceKey_returns401() {
        ResponseEntity<Void> resp = controller.trigger(null, null, body());
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(notificationClient, telegramService);
    }

    @Test
    void trigger_wrongServiceKey_returns401() {
        ResponseEntity<Void> resp = controller.trigger("wrong-key", null, body());
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(notificationClient, telegramService);
    }

    // ── idempotency ───────────────────────────────────────────────────────────

    @Test
    void trigger_duplicateIdempotencyKey_skipsRedelivery() {
        when(userAccountService.getEmailByUuid("owner-1")).thenReturn("owner@test.com");

        ResponseEntity<Void> first = controller.trigger("secret-key", "idem-1", body());
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        verify(notificationClient, times(1)).sendAlertTriggered(anyString(), anyString(), anyString(), anyString());

        reset(notificationClient, telegramService, userAccountService);
        ResponseEntity<Void> second = controller.trigger("secret-key", "idem-1", body());

        assertThat(second.getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(notificationClient, telegramService, userAccountService);
    }

    @Test
    void trigger_noIdempotencyKey_alwaysExecutes() {
        when(userAccountService.getEmailByUuid("owner-1")).thenReturn("owner@test.com");

        controller.trigger("secret-key", null, body());
        controller.trigger("secret-key", null, body());

        verify(notificationClient, times(2)).sendAlertTriggered(anyString(), anyString(), anyString(), anyString());
    }

    // ── delivery paths ────────────────────────────────────────────────────────

    @Test
    void trigger_emailResolved_sendsNotification() {
        when(userAccountService.getEmailByUuid("owner-1")).thenReturn("owner@test.com");

        controller.trigger("secret-key", null, body());

        verify(notificationClient).sendAlertTriggered("owner@test.com", "price", "BTC/USD", "BTC/USD crossed threshold");
    }

    @Test
    void trigger_noEmailFound_skipsEmailDelivery() {
        when(userAccountService.getEmailByUuid("owner-1")).thenReturn(null);

        controller.trigger("secret-key", null, body());

        verify(notificationClient, never()).sendAlertTriggered(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void trigger_telegramConnected_sendsTelegramMessage() {
        when(userAccountService.getEmailByUuid("owner-1")).thenReturn("owner@test.com");
        when(telegramService.isConnected("owner-1", "org-1")).thenReturn(true);

        controller.trigger("secret-key", null, body());

        verify(telegramService).sendMessage("owner-1", "org-1", "BTC/USD crossed threshold");
    }

    @Test
    void trigger_telegramNotConnected_skipsTelegram() {
        when(userAccountService.getEmailByUuid("owner-1")).thenReturn("owner@test.com");
        when(telegramService.isConnected("owner-1", "org-1")).thenReturn(false);

        controller.trigger("secret-key", null, body());

        verify(telegramService, never()).sendMessage(anyString(), anyString(), anyString());
    }

    @Test
    void trigger_telegramSendThrows_stillReturns200() {
        when(userAccountService.getEmailByUuid("owner-1")).thenReturn("owner@test.com");
        when(telegramService.isConnected("owner-1", "org-1")).thenReturn(true);
        doThrow(new IllegalStateException("not connected")).when(telegramService)
                .sendMessage(anyString(), anyString(), anyString());

        ResponseEntity<Void> resp = controller.trigger("secret-key", null, body());

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }
}
