package com.ragagent.connector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramAgentToolTest {

    @Mock TelegramService     telegramService;
    @InjectMocks TelegramAgentTool tool;

    @AfterEach
    void tearDown() {
        // The static ThreadLocals must be cleaned up between tests to prevent state leakage
        tool.clearCurrentEmail();
        tool.clearShareOwnerEmail();
    }

    // ── ThreadLocal email management ──────────────────────────────────────────

    @Test
    void setCurrentEmail_null_setsEmptyString() {
        tool.setCurrentEmail(null);
        when(telegramService.sendMessage(eq(""), isNull(), anyString())).thenReturn("sent");

        String result = tool.sendTelegramMessage("Hello");

        verify(telegramService).sendMessage("", null, "Hello");
    }

    @Test
    void clearCurrentEmail_removesFromThreadLocal() {
        tool.setCurrentEmail("user@example.com");
        tool.clearCurrentEmail();
        when(telegramService.sendMessage(isNull(), isNull(), anyString())).thenReturn("sent");

        tool.sendTelegramMessage("Hello");

        verify(telegramService).sendMessage(null, null, "Hello");
    }

    @Test
    void setShareOwnerEmail_andClear_worksCorrectly() {
        tool.setShareOwnerEmail("owner@example.com");
        tool.clearShareOwnerEmail();
        // After clear, shareOwnerEmail is null → falls back to sendTelegramMessage
        when(telegramService.sendMessage(isNull(), isNull(), anyString())).thenReturn("sent");

        tool.createTelegramGroupSession("message");

        verify(telegramService).sendMessage(null, null, "message");
    }

    // ── sendTelegramMessage ────────────────────────────────────────────────────

    @Test
    void sendTelegramMessage_success_returnsConfirmation() {
        tool.setCurrentEmail("user@example.com");
        when(telegramService.sendMessage("user@example.com", null, "Hello Telegram!"))
                .thenReturn("Message delivered");

        String result = tool.sendTelegramMessage("Hello Telegram!");

        assertThat(result).isEqualTo("Message delivered");
    }

    @Test
    void sendTelegramMessage_notConnected_returnsErrorMessage() {
        tool.setCurrentEmail("user@example.com");
        when(telegramService.sendMessage(anyString(), any(), anyString()))
                .thenThrow(new IllegalStateException("Telegram not connected"));

        String result = tool.sendTelegramMessage("Hello");

        assertThat(result).contains("Could not send Telegram message");
        assertThat(result).contains("Telegram not connected");
    }

    // ── createTelegramGroupSession ─────────────────────────────────────────────

    @Test
    void createTelegramGroupSession_noShareOwner_fallsBackToSendMessage() {
        tool.setCurrentEmail("visitor@example.com");
        // shareOwnerEmail not set → null → blank → fallback
        when(telegramService.sendMessage("visitor@example.com", null, "Group message"))
                .thenReturn("sent to visitor");

        String result = tool.createTelegramGroupSession("Group message");

        assertThat(result).isEqualTo("sent to visitor");
        verify(telegramService, never()).sendGroupNotification(anyString(), anyString(), any(), anyString());
    }

    @Test
    void createTelegramGroupSession_withShareOwner_sendsGroupNotification() {
        tool.setCurrentEmail("visitor@example.com");
        tool.setShareOwnerEmail("owner@example.com");
        when(telegramService.sendGroupNotification("owner@example.com", "visitor@example.com", null, "Group message"))
                .thenReturn("Notified both parties");

        String result = tool.createTelegramGroupSession("Group message");

        assertThat(result).isEqualTo("Notified both parties");
        verify(telegramService).sendGroupNotification("owner@example.com", "visitor@example.com", null, "Group message");
    }

    @Test
    void createTelegramGroupSession_groupNotificationFails_returnsErrorMessage() {
        tool.setCurrentEmail("visitor@example.com");
        tool.setShareOwnerEmail("owner@example.com");
        when(telegramService.sendGroupNotification(anyString(), anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("Network error"));

        String result = tool.createTelegramGroupSession("Group message");

        assertThat(result).contains("Could not create Telegram group session");
    }
}
