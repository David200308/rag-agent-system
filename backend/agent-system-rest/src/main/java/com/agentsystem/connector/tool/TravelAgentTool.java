package com.agentsystem.connector.tool;

import com.agentsystem.agent.ToolCallBudget;
import com.agentsystem.travel.dto.TravelRecordDto;
import com.agentsystem.travel.service.TravelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring AI tool: read the caller's own travel trip records.
 *
 * Each trip has an "Allow Chat" flag (default off) that the user sets from the
 * trip detail panel — only trips with that flag on are ever returned here, so
 * this tool is a hard privacy boundary, not just a hint to the LLM.
 *
 * The caller's user_uuid is injected per-request via setCurrentUserUuid / clearCurrentUserUuid
 * (same ThreadLocal pattern used by the Google Workspace / Telegram tools).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TravelAgentTool {

    private final TravelService  travelService;
    private final ToolCallBudget toolCallBudget;

    private static final ThreadLocal<String> CURRENT_USER_UUID = new ThreadLocal<>();

    public void setCurrentUserUuid(String uuid) { CURRENT_USER_UUID.set(uuid != null ? uuid : ""); }
    public void clearCurrentUserUuid()          { CURRENT_USER_UUID.remove(); }

    /**
     * Lists the user's trips that have been explicitly marked visible to chat.
     *
     * @return a text summary of each chat-visible trip, or a message saying none are visible
     */
    @Tool(description = """
            Retrieves the caller's own travel trip data (title, dates, route, notes,
            expense summary). Call this immediately for any question about the user's
            trips, travel history, itinerary, flights, or travel expenses — do not
            answer such questions without calling it first, since only its actual
            return value tells you which trips (if any) are currently accessible.
            """)
    public String getTravelRecords() {
        if (!toolCallBudget.tryConsume()) return ToolCallBudget.EXHAUSTED_MESSAGE;
        String uuid = CURRENT_USER_UUID.get();
        if (uuid == null || uuid.isBlank()) return "No travel records available.";

        var visible = travelService.listChatVisible(uuid);
        log.info("[TravelAgentTool] {} chat-visible trip(s) for user '{}'", visible.size(), uuid);
        if (visible.isEmpty()) {
            return "The user has no travel trips marked visible to chat.";
        }
        return visible.stream()
                .map(this::formatRecord)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private String formatRecord(TravelRecordDto r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Trip: ").append(r.title()).append("\n");
        sb.append("Dates: ").append(r.startDate()).append(" to ").append(r.endDate()).append("\n");

        if (r.stops() != null && !r.stops().isEmpty()) {
            String route = r.stops().stream()
                    .map(s -> String.valueOf(s.getOrDefault("city", "")))
                    .filter(c -> !c.isBlank())
                    .collect(Collectors.joining(" -> "));
            if (!route.isBlank()) sb.append("Route: ").append(route).append("\n");
        }

        if (r.notes() != null && !r.notes().isBlank()) {
            sb.append("Notes: ").append(r.notes()).append("\n");
        }

        if (r.expenses() != null && !r.expenses().isEmpty()) {
            Map<String, Object> first = r.expenses().get(0);
            Object currencies = first.get("currencies");
            sb.append("Expenses: ").append(r.expenses().size()).append(" expense record(s) logged")
                    .append(currencies != null ? " in currencies " + currencies : "").append(".\n");
        }

        return sb.toString().stripTrailing();
    }
}
