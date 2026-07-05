package com.agentsystem.workflow.service;

import com.agentsystem.config.SchedulerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Calls the Go scheduler's internal REST API on behalf of workflow agents.
 * Uses X-Scheduler-Key authentication — no JWT needed.
 */
@Slf4j
@Component
public class WorkflowScheduleClient {

    private final RestClient restClient;
    private final SchedulerProperties props;

    public WorkflowScheduleClient(SchedulerProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
                .baseUrl(props.url())
                .defaultHeader("X-Scheduler-Key", props.serviceKey())
                .build();
    }

    /** Create a schedule on behalf of ownerUuid. Returns a human-readable result string. */
    public String createSchedule(String ownerUuid, String conversationId, String message,
                                  String cronExpr, String timezone, int topK,
                                  boolean useKnowledgeBase, boolean useWebFetch) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("ownerUuid",       ownerUuid);
            body.put("conversationId",   conversationId);
            body.put("message",          message);
            body.put("cronExpr",         cronExpr.isBlank() ? "0 9 * * *" : cronExpr);
            body.put("timezone",         timezone.isBlank()  ? "UTC"       : timezone);
            body.put("topK",             topK > 0 ? topK : 5);
            body.put("useKnowledgeBase", useKnowledgeBase);
            body.put("useWebFetch",      useWebFetch);

            String response = restClient.post()
                    .uri("/internal/schedules")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            String id = extractField(response, "id");
            return "Schedule created. ID: " + id + ", cron: " + cronExpr + ", timezone: " + timezone;
        } catch (Exception e) {
            log.warn("[WorkflowScheduleClient] createSchedule failed: {}", e.getMessage());
            return "Failed to create schedule: " + e.getMessage();
        }
    }

    /** List schedules for a conversation. Returns JSON array as a string. */
    public String listSchedules(String ownerUuid, String conversationId) {
        try {
            String response = restClient.get()
                    .uri("/internal/schedules?conversationId={cid}&ownerUuid={uuid}",
                            conversationId, ownerUuid)
                    .retrieve()
                    .body(String.class);
            return response != null ? response : "[]";
        } catch (Exception e) {
            log.warn("[WorkflowScheduleClient] listSchedules failed: {}", e.getMessage());
            return "Failed to list schedules: " + e.getMessage();
        }
    }

    /** Delete a schedule by ID. Returns a human-readable result string. */
    public String deleteSchedule(String ownerUuid, String scheduleId) {
        try {
            restClient.delete()
                    .uri("/internal/schedules/{id}?ownerUuid={uuid}", scheduleId, ownerUuid)
                    .retrieve()
                    .toBodilessEntity();
            return "Schedule " + scheduleId + " deleted successfully.";
        } catch (Exception e) {
            log.warn("[WorkflowScheduleClient] deleteSchedule failed: {}", e.getMessage());
            return "Failed to delete schedule " + scheduleId + ": " + e.getMessage();
        }
    }

    private String extractField(String json, String field) {
        if (json == null) return "unknown";
        String key = "\"" + field + "\":\"";
        int idx = json.indexOf(key);
        if (idx < 0) return "unknown";
        int start = idx + key.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : "unknown";
    }
}
