package com.ragagent.controller;

import com.ragagent.sandbox.SandboxService;
import com.ragagent.workflow.WorkflowRunService;
import com.ragagent.workflow.WorkflowService;
import com.ragagent.workflow.entity.Workflow;
import com.ragagent.workflow.entity.WorkflowAgent;
import com.ragagent.workflow.entity.WorkflowRun;
import com.ragagent.workflow.entity.WorkflowRunLog;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowControllerTest {

    @Mock WorkflowService    workflowService;
    @Mock WorkflowRunService runService;
    @Mock SandboxService     sandboxService;
    @Mock HttpServletRequest request;
    @InjectMocks WorkflowController controller;

    private Workflow makeWorkflow(String id) {
        Workflow w = new Workflow();
        w.setId(id);
        w.setName("Test Workflow");
        return w;
    }

    // ── listWorkflows ──────────────────────────────────────────────────────────

    @Test
    void listWorkflows_returnsOwnedWorkflows() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        when(workflowService.listByOwner("user@example.com")).thenReturn(List.of(makeWorkflow("wf-1")));

        ResponseEntity<List<Workflow>> resp = controller.listWorkflows(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    void listWorkflows_noEmailAttribute_usesAnonymous() {
        when(request.getAttribute("authenticatedEmail")).thenReturn(null);
        when(workflowService.listByOwner("anonymous")).thenReturn(List.of());

        ResponseEntity<List<Workflow>> resp = controller.listWorkflows(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    // ── getWorkflow ────────────────────────────────────────────────────────────

    @Test
    void getWorkflow_found_returns200() {
        when(workflowService.findById("wf-1")).thenReturn(Optional.of(makeWorkflow("wf-1")));

        ResponseEntity<Workflow> resp = controller.getWorkflow("wf-1");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void getWorkflow_notFound_returns404() {
        when(workflowService.findById("missing-id")).thenReturn(Optional.empty());

        ResponseEntity<Workflow> resp = controller.getWorkflow("missing-id");

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── createWorkflow ─────────────────────────────────────────────────────────

    @Test
    void createWorkflow_success_returns200() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        Workflow created = makeWorkflow("wf-new");
        when(workflowService.create(anyString(), anyString(), anyString(),
                any(Workflow.AgentPattern.class), any()))
                .thenReturn(created);

        Map<String, Object> body = Map.of(
                "name",         "My Workflow",
                "description",  "Test",
                "agentPattern", "ORCHESTRATOR"
        );

        ResponseEntity<Workflow> resp = controller.createWorkflow(body, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().getId()).isEqualTo("wf-new");
    }

    // ── updateWorkflow ─────────────────────────────────────────────────────────

    @Test
    void updateWorkflow_success_returns200() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");
        Workflow updated = makeWorkflow("wf-1");
        updated.setName("Updated Name");
        when(workflowService.update(eq("wf-1"), eq("owner@example.com"), any()))
                .thenReturn(updated);

        ResponseEntity<Workflow> resp = controller.updateWorkflow("wf-1", Map.of("name", "Updated Name"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateWorkflow_notOwner_returns403() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("other@example.com");
        when(workflowService.update(anyString(), anyString(), any()))
                .thenThrow(new SecurityException("not owner"));

        ResponseEntity<Workflow> resp = controller.updateWorkflow("wf-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void updateWorkflow_notFound_returns404() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        when(workflowService.update(anyString(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("not found"));

        ResponseEntity<Workflow> resp = controller.updateWorkflow("wf-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── deleteWorkflow ─────────────────────────────────────────────────────────

    @Test
    void deleteWorkflow_success_returns204() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");

        ResponseEntity<Void> resp = controller.deleteWorkflow("wf-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void deleteWorkflow_notOwner_returns403() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("other@example.com");
        doThrow(new SecurityException("not owner")).when(workflowService).delete(anyString(), anyString());

        ResponseEntity<Void> resp = controller.deleteWorkflow("wf-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── listAgents ─────────────────────────────────────────────────────────────

    @Test
    void listAgents_returnsAgentList() {
        WorkflowAgent agent = new WorkflowAgent();
        when(workflowService.listAgents("wf-1")).thenReturn(List.of(agent));

        ResponseEntity<List<WorkflowAgent>> resp = controller.listAgents("wf-1");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    // ── deleteAgent ────────────────────────────────────────────────────────────

    @Test
    void deleteAgent_returns204() {
        ResponseEntity<Void> resp = controller.deleteAgent("wf-1", 42L);
        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    // ── listRuns ───────────────────────────────────────────────────────────────

    @Test
    void listRuns_returnsRunList() {
        when(runService.getRuns("wf-1")).thenReturn(List.of(new WorkflowRun()));

        ResponseEntity<List<WorkflowRun>> resp = controller.listRuns("wf-1");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    // ── startRun ──────────────────────────────────────────────────────────────

    @Test
    void startRun_blankUserInput_returns400() {
        ResponseEntity<Map<String, String>> resp = controller.startRun("wf-1", Map.of("userInput", " "), request);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void startRun_missingUserInput_returns400() {
        ResponseEntity<Map<String, String>> resp = controller.startRun("wf-1", Map.of(), request);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void startRun_success_returnsRunId() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        when(runService.startRun(anyString(), anyString(), anyString(), anyBoolean())).thenReturn("run-xyz");

        ResponseEntity<Map<String, String>> resp = controller.startRun("wf-1",
                Map.of("userInput", "Run this!"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("runId", "run-xyz");
    }

    // ── getLogs ────────────────────────────────────────────────────────────────

    @Test
    void getLogs_returnsLogList() {
        when(runService.getLogs("run-123")).thenReturn(List.of(new WorkflowRunLog()));

        ResponseEntity<List<WorkflowRunLog>> resp = controller.getLogs("run-123");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    // ── sandboxStatus ──────────────────────────────────────────────────────────

    @Test
    void sandboxStatus_returnsSandboxStatus() {
        SandboxService.SandboxStatus status = new SandboxService.SandboxStatus(3, 1, 0, 10);
        when(sandboxService.status()).thenReturn(status);

        ResponseEntity<SandboxService.SandboxStatus> resp = controller.sandboxStatus();

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().maxConcurrent()).isEqualTo(3);
    }
}
