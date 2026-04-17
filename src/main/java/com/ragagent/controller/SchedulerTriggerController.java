package com.ragagent.controller;

import com.ragagent.agent.RagAgentGraph;
import com.ragagent.agent.state.AgentState;
import com.ragagent.config.SchedulerProperties;
import com.ragagent.conversation.ConversationService;
import com.ragagent.schema.AgentRequest;
import com.ragagent.schema.AgentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal endpoint called by the Go scheduler service on cron fire.
 *
 * Auth: validated via {@code X-Scheduler-Key} header — NOT a user JWT.
 *       The path /api/v1/scheduler/** is exempt from AuthFilter (see AuthFilter).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/scheduler")
@RequiredArgsConstructor
@Tag(name = "Scheduler", description = "Internal trigger endpoint for the Go scheduler service")
public class SchedulerTriggerController {

    private final RagAgentGraph         agentGraph;
    private final ConversationService   conversationService;
    private final SchedulerProperties   schedulerProperties;

    /** Body sent by the Go scheduler when a cron fires. */
    public record TriggerRequest(
            String  userEmail,
            String  conversationId,
            String  message,
            int     topK,
            boolean useKnowledgeBase,
            boolean useWebFetch
    ) {}

    @PostMapping(value = "/trigger",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Execute a scheduled query on behalf of a user (internal, service-key protected)")
    public ResponseEntity<AgentResponse> trigger(
            @RequestHeader(value = "X-Scheduler-Key", required = false) String serviceKey,
            @RequestBody TriggerRequest body) {

        // Validate shared service key
        if (serviceKey == null || !serviceKey.equals(schedulerProperties.serviceKey())) {
            log.warn("[SchedulerTrigger] Rejected request — invalid or missing X-Scheduler-Key");
            return ResponseEntity.status(401).build();
        }

        String runId = UUID.randomUUID().toString();
        log.info("[SchedulerTrigger] Firing runId={} conv={} user={} message='{}'",
                runId, body.conversationId(), body.userEmail(), body.message());

        String conversationId = conversationService.resolveConversation(
                body.conversationId(), body.userEmail());
        conversationService.saveUserMessage(conversationId, body.message());

        try {
            AgentRequest agentRequest = new AgentRequest(
                    body.message(),
                    null,
                    body.topK() > 0 ? body.topK() : 5,
                    conversationService.loadHistory(conversationId),
                    false,
                    conversationId,
                    List.of(),
                    body.useKnowledgeBase(),
                    body.useWebFetch()
            );

            Map<String, Object> initData = new HashMap<>();
            initData.put("request", agentRequest);
            initData.put("runId", runId);
            if (body.userEmail() != null) {
                initData.put("userEmail", body.userEmail());
            }

            var result = agentGraph.getGraph()
                    .invoke(initData, RunnableConfig.builder().build());

            AgentState finalState = result.orElseThrow(
                    () -> new RuntimeException("Graph produced no output"));
            AgentResponse raw = finalState.response().orElseThrow(
                    () -> new RuntimeException("No response in final state"));

            conversationService.saveAssistantMessage(conversationId, raw.answer(), runId);

            AgentResponse response = withConversationId(raw, conversationId);
            log.info("[SchedulerTrigger] Completed runId={} conv={}", runId, conversationId);
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            log.error("[SchedulerTrigger] Pipeline error runId={}: {}", runId, ex.getMessage(), ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    private AgentResponse withConversationId(AgentResponse raw, String conversationId) {
        AgentResponse.RunMetadata meta = raw.metadata();
        return new AgentResponse(
                raw.answer(),
                raw.sources(),
                raw.routeDecision(),
                raw.fallbackActivated(),
                raw.fallbackReason(),
                new AgentResponse.RunMetadata(
                        meta.runId(), meta.startedAt(), meta.durationMs(),
                        meta.documentsRetrieved(), meta.modelUsed(), conversationId)
        );
    }
}
