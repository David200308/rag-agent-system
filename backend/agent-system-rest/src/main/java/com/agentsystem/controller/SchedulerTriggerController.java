package com.agentsystem.controller;

import com.agentsystem.agent.AgentSystemGraph;
import com.agentsystem.agent.state.AgentState;
import com.agentsystem.config.SchedulerProperties;
import com.agentsystem.conversation.service.ConversationService;
import com.agentsystem.org.OrgContext;
import com.agentsystem.schema.AgentRequest;
import com.agentsystem.schema.AgentResponse;
import com.agentsystem.workflow.service.WorkflowRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
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

    private final AgentSystemGraph         agentGraph;
    private final ConversationService   conversationService;
    private final SchedulerProperties   schedulerProperties;
    private final WorkflowRunService    workflowRunService;
    private final StringRedisTemplate   redisTemplate;

    private static final String IDEMPOTENCY_KEY_PREFIX = "scheduler:idempotency:";
    private static final Duration IDEMPOTENCY_TTL      = Duration.ofHours(24);

    /** Body sent by the Go scheduler when a cron fires. */
    public record TriggerRequest(
            String  userUuid,
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
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody TriggerRequest body) {

        // Validate shared service key
        if (serviceKey == null || !serviceKey.equals(schedulerProperties.serviceKey())) {
            log.warn("[SchedulerTrigger] Rejected request — invalid or missing X-Scheduler-Key");
            return ResponseEntity.status(401).build();
        }

        // Asynq retries (MaxRetry=3) re-call this endpoint with the same idempotency key
        // if a prior attempt times out after the message was already sent — without this
        // check, a retry would re-run the whole pipeline and send a duplicate message.
        if (idempotencyKey != null && !claimIdempotencyKey(idempotencyKey)) {
            log.info("[SchedulerTrigger] Duplicate request for idempotencyKey={} — skipping re-execution", idempotencyKey);
            return ResponseEntity.ok().build();
        }

        String runId = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();
        log.info("[SchedulerTrigger] Firing runId={} conv={} user={} message='{}'",
                runId, body.conversationId(), body.userUuid(), body.message());

        OrgContext ctx = new OrgContext(body.userUuid(), null, "PERSONAL", null);
        String conversationId = conversationService.resolveConversation(
                body.conversationId(), ctx);
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
            if (body.userUuid() != null) {
                initData.put("userUuid", body.userUuid());
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

    /** Body sent by the Go scheduler when a workflow cron fires. */
    public record WorkflowTriggerRequest(
            String userUuid,
            String workflowId,
            String workflowInput
    ) {}

    @PostMapping(value = "/workflow-trigger",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Start a scheduled workflow run on behalf of a user (internal, service-key protected)")
    public ResponseEntity<Map<String, String>> workflowTrigger(
            @RequestHeader(value = "X-Scheduler-Key", required = false) String serviceKey,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody WorkflowTriggerRequest body) {

        if (serviceKey == null || !serviceKey.equals(schedulerProperties.serviceKey())) {
            log.warn("[SchedulerTrigger] Rejected workflow-trigger — invalid or missing X-Scheduler-Key");
            return ResponseEntity.status(401).build();
        }

        if (idempotencyKey != null && !claimIdempotencyKey(idempotencyKey)) {
            log.info("[SchedulerTrigger] Duplicate workflow-trigger for idempotencyKey={} — skipping re-execution",
                    idempotencyKey);
            return ResponseEntity.ok(Map.of("status", "duplicate"));
        }

        log.info("[SchedulerTrigger] workflow-trigger workflowId={} user={}", body.workflowId(), body.userUuid());

        String runId = workflowRunService.startRunByUuid(
                body.workflowId(), body.workflowInput(),
                body.userUuid() != null ? body.userUuid() : "anonymous",
                false);
        return ResponseEntity.ok(Map.of("runId", runId));
    }

    /** Returns true the first time this key is seen (caller should proceed); false on a repeat. */
    private boolean claimIdempotencyKey(String idempotencyKey) {
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(IDEMPOTENCY_KEY_PREFIX + idempotencyKey, "1", IDEMPOTENCY_TTL);
        return Boolean.TRUE.equals(isNew);
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
