package com.ragagent.connector;

import com.ragagent.agent.ToolCallBudget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarAgentToolTest {

    @Mock GoogleCalendarService calendarService;
    @Mock ToolCallBudget        toolCallBudget;
    @InjectMocks GoogleCalendarAgentTool tool;

    @BeforeEach
    void setUp() {
        lenient().when(toolCallBudget.tryConsume()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        tool.clearCurrentEmail();
        tool.clearCurrentOrgId();
    }

    @Test
    void createCalendarEvent_budgetExhausted_returnsExhaustedMessageWithoutCallingService() {
        when(toolCallBudget.tryConsume()).thenReturn(false);

        String result = tool.createCalendarEvent("Meeting", "2025-06-10T14:00:00Z", "2025-06-10T15:00:00Z", null, null);

        assertThat(result).isEqualTo(ToolCallBudget.EXHAUSTED_MESSAGE);
        verifyNoInteractions(calendarService);
    }

    // ── ThreadLocal management ────────────────────────────────────────────────

    @Test
    void setCurrentEmail_null_setsEmptyString() {
        tool.setCurrentEmail(null);
        when(calendarService.listEvents(eq(""), isNull(), anyInt()))
                .thenReturn("No events");

        String result = tool.listUpcomingEvents(5);

        assertThat(result).isEqualTo("No events");
    }

    @Test
    void setCurrentOrgId_isUsedInServiceCall() {
        tool.setCurrentEmail("user@example.com");
        tool.setCurrentOrgId("org-123");
        when(calendarService.listEvents("user@example.com", "org-123", 5))
                .thenReturn("org events");

        String result = tool.listUpcomingEvents(5);

        verify(calendarService).listEvents("user@example.com", "org-123", 5);
        assertThat(result).isEqualTo("org events");
    }

    // ── listUpcomingEvents ────────────────────────────────────────────────────

    @Test
    void listUpcomingEvents_success_returnsEvents() {
        tool.setCurrentEmail("user@example.com");
        when(calendarService.listEvents("user@example.com", null, 10))
                .thenReturn("Upcoming events:\n- Meeting at 14:00");

        String result = tool.listUpcomingEvents(10);

        assertThat(result).contains("Meeting");
    }

    @Test
    void listUpcomingEvents_serviceThrows_returnsErrorMessage() {
        tool.setCurrentEmail("user@example.com");
        when(calendarService.listEvents(anyString(), any(), anyInt()))
                .thenThrow(new IllegalStateException("Google account not connected. Visit /mcp to connect."));

        String result = tool.listUpcomingEvents(5);

        assertThat(result).contains("Could not list calendar events");
        assertThat(result).contains("Google account not connected");
    }

    @Test
    void listUpcomingEvents_maxResultsClamped_doesNotExceed25() {
        tool.setCurrentEmail("user@example.com");
        when(calendarService.listEvents("user@example.com", null, 25))
                .thenReturn("events");

        tool.listUpcomingEvents(100);

        verify(calendarService).listEvents("user@example.com", null, 25);
    }

    @Test
    void listUpcomingEvents_maxResultsBelowMin_clampedTo1() {
        tool.setCurrentEmail("user@example.com");
        when(calendarService.listEvents("user@example.com", null, 1))
                .thenReturn("events");

        tool.listUpcomingEvents(0);

        verify(calendarService).listEvents("user@example.com", null, 1);
    }

    // ── createCalendarEvent ───────────────────────────────────────────────────

    @Test
    void createCalendarEvent_success_returnsConfirmation() {
        tool.setCurrentEmail("user@example.com");
        when(calendarService.createEvent("user@example.com", null,
                "Team Meeting", "2025-06-10T14:00:00Z", "2025-06-10T15:00:00Z", "Agenda", "Office"))
                .thenReturn("Event created: Team Meeting. View it at: https://calendar.google.com/event");

        String result = tool.createCalendarEvent(
                "Team Meeting", "2025-06-10T14:00:00Z", "2025-06-10T15:00:00Z", "Agenda", "Office");

        assertThat(result).contains("Team Meeting");
        assertThat(result).contains("calendar.google.com");
    }

    @Test
    void createCalendarEvent_serviceThrows_returnsErrorMessage() {
        tool.setCurrentEmail("user@example.com");
        when(calendarService.createEvent(anyString(), any(), anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new IllegalStateException("Not connected to Google Calendar"));

        String result = tool.createCalendarEvent(
                "Meeting", "2025-06-10T14:00:00Z", "2025-06-10T15:00:00Z", null, null);

        assertThat(result).contains("Could not create calendar event");
        assertThat(result).contains("Not connected to Google Calendar");
    }
}
