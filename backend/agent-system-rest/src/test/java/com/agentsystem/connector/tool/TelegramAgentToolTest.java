package com.agentsystem.connector.tool;

import com.agentsystem.connector.service.TelegramService;

import com.agentsystem.agent.ToolCallBudget;
import com.agentsystem.user.entity.User;
import com.agentsystem.user.entity.UserStatus;
import com.agentsystem.user.service.UserAccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramAgentToolTest {

    @Mock TelegramService     telegramService;
    @Mock ToolCallBudget      toolCallBudget;
    @Mock UserAccountService  userAccountService;
    @InjectMocks TelegramAgentTool tool;

    @BeforeEach
    void setUp() {
        lenient().when(toolCallBudget.tryConsume()).thenReturn(true);
        // resolveUuid() still bridges the (currently unwired) shareOwnerEmail channel.
        lenient().when(userAccountService.findByEmail(anyString()))
                .thenAnswer(inv -> Optional.of(new User(inv.getArgument(0), inv.getArgument(0), UserStatus.USER, true)));
    }

    @AfterEach
    void tearDown() {
        // The static ThreadLocals must be cleaned up between tests to prevent state leakage
        tool.clearCurrentUserUuid();
        tool.clearShareOwnerEmail();
    }

    @Test
    void sendTelegramMessage_budgetExhausted_returnsExhaustedMessageWithoutCallingService() {
        when(toolCallBudget.tryConsume()).thenReturn(false);

        String result = tool.sendTelegramMessage("Hello");

        assertThat(result).isEqualTo(ToolCallBudget.EXHAUSTED_MESSAGE);
        verifyNoInteractions(telegramService);
    }

    // ── ThreadLocal user_uuid management ────────────────────────────────────────

    @Test
    void setCurrentUserUuid_null_setsEmptyString() {
        tool.setCurrentUserUuid(null);
        when(telegramService.sendMessage(eq(""), isNull(), anyString())).thenReturn("sent");

        String result = tool.sendTelegramMessage("Hello");

        verify(telegramService).sendMessage("", null, "Hello");
    }

    @Test
    void clearCurrentUserUuid_removesFromThreadLocal() {
        tool.setCurrentUserUuid("user-uuid-1");
        tool.clearCurrentUserUuid();
        // After clear, the ThreadLocal is unset (null) — passed straight through to the
        // service, which normalizes null to "" internally.
        when(telegramService.sendMessage(isNull(), isNull(), anyString())).thenReturn("sent");

        tool.sendTelegramMessage("Hello");

        verify(telegramService).sendMessage(null, null, "Hello");
    }

    @Test
    void setShareOwnerEmail_andClear_worksCorrectly() {
        tool.setShareOwnerEmail("owner@example.com");
        tool.clearShareOwnerEmail();
        // After clear, shareOwnerEmail is null → falls back to sendTelegramMessage.
        // CURRENT_USER_UUID was never set in this test, so it's null too.
        when(telegramService.sendMessage(isNull(), isNull(), anyString())).thenReturn("sent");

        tool.createTelegramGroupSession("message");

        verify(telegramService).sendMessage(null, null, "message");
    }

    // ── sendTelegramMessage ────────────────────────────────────────────────────

    @Test
    void sendTelegramMessage_success_returnsConfirmation() {
        tool.setCurrentUserUuid("user-uuid-1");
        when(telegramService.sendMessage("user-uuid-1", null, "Hello Telegram!"))
                .thenReturn("Message delivered");

        String result = tool.sendTelegramMessage("Hello Telegram!");

        assertThat(result).isEqualTo("Message delivered");
    }

    @Test
    void sendTelegramMessage_notConnected_returnsErrorMessage() {
        tool.setCurrentUserUuid("user-uuid-1");
        when(telegramService.sendMessage(anyString(), any(), anyString()))
                .thenThrow(new IllegalStateException("Telegram not connected"));

        String result = tool.sendTelegramMessage("Hello");

        assertThat(result).contains("Could not send Telegram message");
        assertThat(result).contains("Telegram not connected");
    }

    // ── createTelegramGroupSession ─────────────────────────────────────────────

    @Test
    void createTelegramGroupSession_noShareOwner_fallsBackToSendMessage() {
        tool.setCurrentUserUuid("visitor-uuid-1");
        // shareOwnerEmail not set → null → blank → fallback
        when(telegramService.sendMessage("visitor-uuid-1", null, "Group message"))
                .thenReturn("sent to visitor");

        String result = tool.createTelegramGroupSession("Group message");

        assertThat(result).isEqualTo("sent to visitor");
        verify(telegramService, never()).sendGroupNotification(anyString(), anyString(), any(), anyString());
    }

    @Test
    void createTelegramGroupSession_withShareOwner_sendsGroupNotification() {
        tool.setCurrentUserUuid("visitor-uuid-1");
        tool.setShareOwnerEmail("owner@example.com");
        when(telegramService.sendGroupNotification("owner@example.com", "visitor-uuid-1", null, "Group message"))
                .thenReturn("Notified both parties");

        String result = tool.createTelegramGroupSession("Group message");

        assertThat(result).isEqualTo("Notified both parties");
        verify(telegramService).sendGroupNotification("owner@example.com", "visitor-uuid-1", null, "Group message");
    }

    @Test
    void createTelegramGroupSession_groupNotificationFails_returnsErrorMessage() {
        tool.setCurrentUserUuid("visitor-uuid-1");
        tool.setShareOwnerEmail("owner@example.com");
        when(telegramService.sendGroupNotification(anyString(), anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("Network error"));

        String result = tool.createTelegramGroupSession("Group message");

        assertThat(result).contains("Could not create Telegram group session");
    }
}
