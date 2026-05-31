package com.ragagent.controller;

import com.ragagent.agent.RagAgentGraph;
import com.ragagent.config.SchedulerProperties;
import com.ragagent.conversation.ConversationService;
import com.ragagent.workflow.WorkflowRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulerTriggerControllerTest {

    @Mock RagAgentGraph       agentGraph;
    @Mock ConversationService conversationService;
    @Mock WorkflowRunService  workflowRunService;

    // SchedulerProperties is a record (final) — instantiate directly
    SchedulerProperties schedulerProperties = new SchedulerProperties("secret-key", "http://scheduler");

    SchedulerTriggerController controller;

    @BeforeEach
    void setUp() {
        controller = new SchedulerTriggerController(agentGraph, conversationService,
                schedulerProperties, workflowRunService);
    }

    // ── /trigger — service key validation ─────────────────────────────────────

    @Test
    void trigger_nullServiceKey_returns401() {
        var body = new SchedulerTriggerController.TriggerRequest(
                "user@example.com", "conv-1", "Hello?", 5, true, false);

        ResponseEntity<?> resp = controller.trigger(null, body);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void trigger_wrongServiceKey_returns401() {
        var body = new SchedulerTriggerController.TriggerRequest(
                "user@example.com", "conv-1", "Hello?", 5, true, false);

        ResponseEntity<?> resp = controller.trigger("wrong-key", body);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    // ── /workflow-trigger — service key validation ─────────────────────────────

    @Test
    void workflowTrigger_nullServiceKey_returns401() {
        var body = new SchedulerTriggerController.WorkflowTriggerRequest(
                "user@example.com", "wf-123", "Run this");

        ResponseEntity<?> resp = controller.workflowTrigger(null, body);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void workflowTrigger_wrongKey_returns401() {
        var body = new SchedulerTriggerController.WorkflowTriggerRequest(
                "user@example.com", "wf-123", "Run this");

        ResponseEntity<?> resp = controller.workflowTrigger("bad-key", body);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void workflowTrigger_validKey_returnsRunId() {
        when(workflowRunService.startRun(eq("wf-123"), eq("Run this"), anyString(), anyBoolean()))
                .thenReturn("run-abc");

        var body = new SchedulerTriggerController.WorkflowTriggerRequest(
                "user@example.com", "wf-123", "Run this");

        ResponseEntity<Map<String, String>> resp = controller.workflowTrigger("secret-key", body);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("runId", "run-abc");
    }

    @Test
    void workflowTrigger_nullUserEmail_usesAnonymous() {
        when(workflowRunService.startRun(anyString(), anyString(), eq("anonymous"), anyBoolean()))
                .thenReturn("run-xyz");

        var body = new SchedulerTriggerController.WorkflowTriggerRequest(
                null, "wf-123", "Run this");

        ResponseEntity<Map<String, String>> resp = controller.workflowTrigger("secret-key", body);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("runId", "run-xyz");
    }
}
