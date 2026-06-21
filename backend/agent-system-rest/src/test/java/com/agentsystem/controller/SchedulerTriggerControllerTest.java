package com.agentsystem.controller;

import com.agentsystem.agent.AgentSystemGraph;
import com.agentsystem.config.SchedulerProperties;
import com.agentsystem.conversation.ConversationService;
import com.agentsystem.schema.AgentResponse;
import com.agentsystem.workflow.WorkflowRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerTriggerControllerTest {

    @Mock AgentSystemGraph       agentGraph;
    @Mock ConversationService conversationService;
    @Mock WorkflowRunService  workflowRunService;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    // SchedulerProperties is a record (final) — instantiate directly
    SchedulerProperties schedulerProperties = new SchedulerProperties("secret-key", "http://scheduler");

    SchedulerTriggerController controller;

    @BeforeEach
    void setUp() {
        // Fake idempotency-key store backed by a plain set — keeps the test a pure
        // unit test, no Redis needed. Mirrors setIfAbsent's real semantics.
        Set<String> claimed = ConcurrentHashMap.newKeySet();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(inv -> claimed.add(inv.getArgument(0)));

        controller = new SchedulerTriggerController(agentGraph, conversationService,
                schedulerProperties, workflowRunService, redisTemplate);
    }

    // ── /trigger — service key validation ─────────────────────────────────────

    @Test
    void trigger_nullServiceKey_returns401() {
        var body = new SchedulerTriggerController.TriggerRequest(
                "user@example.com", "conv-1", "Hello?", 5, true, false);

        ResponseEntity<?> resp = controller.trigger(null, null, body);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void trigger_wrongServiceKey_returns401() {
        var body = new SchedulerTriggerController.TriggerRequest(
                "user@example.com", "conv-1", "Hello?", 5, true, false);

        ResponseEntity<?> resp = controller.trigger("wrong-key", null, body);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    // ── /trigger — idempotency ─────────────────────────────────────────────────

    @Test
    void trigger_duplicateIdempotencyKey_skipsExecutionReturns200() {
        when(conversationService.resolveConversation(anyString(), anyString())).thenReturn("conv-resolved");
        when(conversationService.loadHistory(anyString())).thenReturn(List.of());
        when(agentGraph.getGraph()).thenThrow(new RuntimeException("should not be reached on duplicate"));

        var body = new SchedulerTriggerController.TriggerRequest(
                "user@example.com", "conv-1", "Hello?", 5, true, false);

        ResponseEntity<?> first = controller.trigger("secret-key", "run-dup-1", body);
        // First call still hits the pipeline (which we've stubbed to throw → 500),
        // proving the idempotency check doesn't block a genuinely new key.
        assertThat(first.getStatusCode().value()).isEqualTo(500);

        // Second call with the SAME idempotency key must short-circuit before the
        // pipeline (and therefore before resolveConversation) runs again.
        reset(conversationService);
        ResponseEntity<?> second = controller.trigger("secret-key", "run-dup-1", body);

        assertThat(second.getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(conversationService);
    }

    @Test
    void trigger_noIdempotencyKey_alwaysExecutes() {
        when(conversationService.resolveConversation(anyString(), anyString())).thenReturn("conv-resolved");
        when(conversationService.loadHistory(anyString())).thenReturn(List.of());
        when(agentGraph.getGraph()).thenThrow(new RuntimeException("expected"));

        var body = new SchedulerTriggerController.TriggerRequest(
                "user@example.com", "conv-1", "Hello?", 5, true, false);

        controller.trigger("secret-key", null, body);
        controller.trigger("secret-key", null, body);

        verify(conversationService, times(2)).resolveConversation(anyString(), anyString());
    }

    // ── /workflow-trigger — service key validation ─────────────────────────────

    @Test
    void workflowTrigger_nullServiceKey_returns401() {
        var body = new SchedulerTriggerController.WorkflowTriggerRequest(
                "user@example.com", "wf-123", "Run this");

        ResponseEntity<?> resp = controller.workflowTrigger(null, null, body);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void workflowTrigger_wrongKey_returns401() {
        var body = new SchedulerTriggerController.WorkflowTriggerRequest(
                "user@example.com", "wf-123", "Run this");

        ResponseEntity<?> resp = controller.workflowTrigger("bad-key", null, body);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void workflowTrigger_validKey_returnsRunId() {
        when(workflowRunService.startRun(eq("wf-123"), eq("Run this"), anyString(), anyBoolean()))
                .thenReturn("run-abc");

        var body = new SchedulerTriggerController.WorkflowTriggerRequest(
                "user@example.com", "wf-123", "Run this");

        ResponseEntity<Map<String, String>> resp = controller.workflowTrigger("secret-key", null, body);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("runId", "run-abc");
    }

    @Test
    void workflowTrigger_nullUserEmail_usesAnonymous() {
        when(workflowRunService.startRun(anyString(), anyString(), eq("anonymous"), anyBoolean()))
                .thenReturn("run-xyz");

        var body = new SchedulerTriggerController.WorkflowTriggerRequest(
                null, "wf-123", "Run this");

        ResponseEntity<Map<String, String>> resp = controller.workflowTrigger("secret-key", null, body);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("runId", "run-xyz");
    }

    @Test
    void workflowTrigger_duplicateIdempotencyKey_skipsStartRun() {
        when(workflowRunService.startRun(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn("run-once");
        var body = new SchedulerTriggerController.WorkflowTriggerRequest(
                "user@example.com", "wf-123", "Run this");

        ResponseEntity<Map<String, String>> first = controller.workflowTrigger("secret-key", "run-dup-2", body);
        ResponseEntity<Map<String, String>> second = controller.workflowTrigger("secret-key", "run-dup-2", body);

        assertThat(first.getBody()).containsEntry("runId", "run-once");
        assertThat(second.getBody()).containsEntry("status", "duplicate");
        verify(workflowRunService, times(1)).startRun(anyString(), anyString(), anyString(), anyBoolean());
    }

    // ── withConversationId ────────────────────────────────────────────────────

    @Test
    void withConversationId_injectsConversationIdIntoMetadata() throws Exception {
        AgentResponse.RunMetadata meta = new AgentResponse.RunMetadata(
                "run-1", Instant.now(), 100L, 3, "gpt-4", null);
        AgentResponse raw = new AgentResponse(
                "Answer text", List.of(), new AgentResponse.RouteDecision("DIRECT", "direct", 0.9),
                false, null, meta);

        AgentResponse result = callWithConversationId(raw, "conv-injected");

        assertThat(result.answer()).isEqualTo("Answer text");
        assertThat(result.metadata().conversationId()).isEqualTo("conv-injected");
        assertThat(result.metadata().runId()).isEqualTo("run-1");
        assertThat(result.metadata().documentsRetrieved()).isEqualTo(3);
    }

    @Test
    void withConversationId_preservesAllOtherFields() throws Exception {
        AgentResponse.RunMetadata meta = new AgentResponse.RunMetadata(
                "run-42", Instant.now(), 250L, 5, "claude-opus-4-6", "old-conv");
        AgentResponse.SourceDocument src = new AgentResponse.SourceDocument(
                "doc-1", "content", "file.pdf", 0.9, null);
        AgentResponse raw = new AgentResponse(
                "The answer", List.of(src),
                new AgentResponse.RouteDecision("RETRIEVE", "retrieval", 0.95),
                false, null, meta);

        AgentResponse result = callWithConversationId(raw, "new-conv");

        assertThat(result.sources()).hasSize(1);
        assertThat(result.routeDecision().route()).isEqualTo("RETRIEVE");
        assertThat(result.metadata().conversationId()).isEqualTo("new-conv");
        assertThat(result.metadata().modelUsed()).isEqualTo("claude-opus-4-6");
        assertThat(result.metadata().durationMs()).isEqualTo(250L);
    }

    // ── TriggerRequest record ─────────────────────────────────────────────────

    @Test
    void triggerRequest_record_fields() {
        var req = new SchedulerTriggerController.TriggerRequest(
                "user@test.com", "conv-1", "Hello?", 5, true, false);
        assertThat(req.userEmail()).isEqualTo("user@test.com");
        assertThat(req.conversationId()).isEqualTo("conv-1");
        assertThat(req.message()).isEqualTo("Hello?");
        assertThat(req.topK()).isEqualTo(5);
        assertThat(req.useKnowledgeBase()).isTrue();
        assertThat(req.useWebFetch()).isFalse();
    }

    @Test
    void workflowTriggerRequest_record_fields() {
        var req = new SchedulerTriggerController.WorkflowTriggerRequest(
                "owner@test.com", "wf-1", "Run analysis");
        assertThat(req.userEmail()).isEqualTo("owner@test.com");
        assertThat(req.workflowId()).isEqualTo("wf-1");
        assertThat(req.workflowInput()).isEqualTo("Run analysis");
    }

    // ── trigger: valid key paths ──────────────────────────────────────────────

    @Test
    void trigger_validKey_pipelineThrowsException_returns500() {
        // agentGraph.getGraph() throws — covers the catch(Exception) branch in trigger()
        when(conversationService.resolveConversation(anyString(), anyString())).thenReturn("conv-resolved");
        when(conversationService.loadHistory(anyString())).thenReturn(List.of());
        when(agentGraph.getGraph()).thenThrow(new RuntimeException("Graph init failed"));

        var body = new SchedulerTriggerController.TriggerRequest(
                "user@example.com", "conv-1", "Hello?", 5, true, false);

        ResponseEntity<?> resp = controller.trigger("secret-key", null, body);

        assertThat(resp.getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void trigger_validKey_nullUserEmail_processesWithoutNPE() {
        // null email path: initData.put("userEmail",...) is skipped when null
        // resolveConversation is called with (conversationId, null) when userEmail is null
        when(conversationService.resolveConversation(anyString(), (String) isNull())).thenReturn("conv-resolved");
        when(conversationService.loadHistory(anyString())).thenReturn(List.of());
        when(agentGraph.getGraph()).thenThrow(new RuntimeException("expected failure"));

        var body = new SchedulerTriggerController.TriggerRequest(
                null, "conv-1", "Hello?", 5, true, false);

        ResponseEntity<?> resp = controller.trigger("secret-key", null, body);

        // Still returns 500 — graph failure is handled gracefully
        assertThat(resp.getStatusCode().value()).isEqualTo(500);
    }

    // ── reflection helper ─────────────────────────────────────────────────────

    private AgentResponse callWithConversationId(AgentResponse raw, String convId) throws Exception {
        Method m = SchedulerTriggerController.class.getDeclaredMethod(
                "withConversationId", AgentResponse.class, String.class);
        m.setAccessible(true);
        return (AgentResponse) m.invoke(controller, raw, convId);
    }
}
