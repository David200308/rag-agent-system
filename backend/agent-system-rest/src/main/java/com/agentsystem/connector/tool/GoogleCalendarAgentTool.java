package com.agentsystem.connector.tool;

import com.agentsystem.connector.service.GoogleCalendarService;

import com.agentsystem.agent.ToolCallBudget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Spring AI tools for Google Calendar: list upcoming events and create new events.
 *
 * Email and orgId are injected per-request via ThreadLocals (same pattern as other tools).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleCalendarAgentTool {

    private final GoogleCalendarService googleCalendarService;
    private final ToolCallBudget        toolCallBudget;

    private static final ThreadLocal<String> CURRENT_EMAIL  = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ORG_ID = new ThreadLocal<>();

    public void setCurrentEmail(String email)  { CURRENT_EMAIL.set(email != null ? email : ""); }
    public void clearCurrentEmail()            { CURRENT_EMAIL.remove(); }

    public void setCurrentOrgId(String orgId)  { CURRENT_ORG_ID.set(orgId); }
    public void clearCurrentOrgId()            { CURRENT_ORG_ID.remove(); }

    @Tool(description = """
            List upcoming events from the user's Google Calendar.
            Use this when the user asks about their schedule, upcoming meetings, calendar events,
            or anything related to "what's on my calendar" or "what do I have coming up".
            Returns a formatted list of upcoming events with times and locations.
            Specify maxResults (default 10, max 25) to control how many events are returned.
            """)
    public String listUpcomingEvents(int maxResults) {
        if (!toolCallBudget.tryConsume()) return ToolCallBudget.EXHAUSTED_MESSAGE;
        int limit = Math.min(Math.max(maxResults, 1), 25);
        log.info("[GoogleCalendarAgentTool] Listing {} events for '{}'", limit, CURRENT_EMAIL.get());
        try {
            return googleCalendarService.listEvents(CURRENT_EMAIL.get(), CURRENT_ORG_ID.get(), limit);
        } catch (IllegalStateException e) {
            return "Could not list calendar events: " + e.getMessage();
        }
    }

    @Tool(description = """
            Create a new event on the user's Google Calendar.
            Use this when the user says "schedule a meeting", "add to my calendar", "create an event",
            or similar requests to book time.
            startDateTime and endDateTime must be in ISO 8601 format (e.g. "2025-06-10T14:00:00Z").
            description and location are optional.
            Returns a confirmation with a link to the created event.
            """)
    public String createCalendarEvent(String title, String startDateTime, String endDateTime,
                                      String description, String location) {
        if (!toolCallBudget.tryConsume()) return ToolCallBudget.EXHAUSTED_MESSAGE;
        log.info("[GoogleCalendarAgentTool] Creating event '{}' for '{}'", title, CURRENT_EMAIL.get());
        try {
            return googleCalendarService.createEvent(
                    CURRENT_EMAIL.get(), CURRENT_ORG_ID.get(),
                    title, startDateTime, endDateTime, description, location);
        } catch (IllegalStateException e) {
            return "Could not create calendar event: " + e.getMessage();
        }
    }
}
