package com.agentsystem.workflow.controller;

import com.agentsystem.org.OrgContext;
import com.agentsystem.sandbox.service.SandboxService;
import com.agentsystem.workflow.service.WorkflowRunService;
import com.agentsystem.workflow.service.WorkflowService;
import com.agentsystem.workflow.entity.Workflow;
import com.agentsystem.workflow.entity.WorkflowAgent;
import com.agentsystem.workflow.entity.WorkflowRun;
import com.agentsystem.workflow.entity.WorkflowRunLog;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
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
    void listWorkflows_returnsWorkflows() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);
        when(workflowService.list(any(OrgContext.class))).thenReturn(List.of(makeWorkflow("wf-1")));

        ResponseEntity<List<Workflow>> resp = controller.listWorkflows(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    void listWorkflows_nullEmail_returnsEmptyList() {
        when(request.getAttribute("authenticatedEmail")).thenReturn(null);
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn(null);
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);
        when(workflowService.list(any(OrgContext.class))).thenReturn(List.of());

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
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);
        Workflow created = makeWorkflow("wf-new");
        when(workflowService.create(anyString(), anyString(), any(OrgContext.class),
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

    private void setupRequest(String email) {
        when(request.getAttribute("authenticatedEmail")).thenReturn(email);
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);
    }

    @Test
    void updateWorkflow_success_returns200() {
        setupRequest("owner@example.com");
        Workflow updated = makeWorkflow("wf-1");
        updated.setName("Updated Name");
        when(workflowService.update(eq("wf-1"), any(OrgContext.class), any()))
                .thenReturn(updated);

        ResponseEntity<Workflow> resp = controller.updateWorkflow("wf-1", Map.of("name", "Updated Name"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateWorkflow_notOwner_returns403() {
        setupRequest("other@example.com");
        when(workflowService.update(anyString(), any(OrgContext.class), any()))
                .thenThrow(new SecurityException("not owner"));

        ResponseEntity<Workflow> resp = controller.updateWorkflow("wf-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void updateWorkflow_notFound_returns404() {
        setupRequest("user@example.com");
        when(workflowService.update(anyString(), any(OrgContext.class), any()))
                .thenThrow(new IllegalArgumentException("not found"));

        ResponseEntity<Workflow> resp = controller.updateWorkflow("wf-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── deleteWorkflow ─────────────────────────────────────────────────────────

    @Test
    void deleteWorkflow_success_returns204() {
        setupRequest("owner@example.com");

        ResponseEntity<Void> resp = controller.deleteWorkflow("wf-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void deleteWorkflow_notOwner_returns403() {
        setupRequest("other@example.com");
        doThrow(new SecurityException("not owner")).when(workflowService).delete(anyString(), any(OrgContext.class));

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
        when(runService.getRuns("wf-1", 0, 10))
                .thenReturn(new PageImpl<>(List.of(new WorkflowRun())));

        ResponseEntity<Page<WorkflowRun>> resp = controller.listRuns("wf-1", 0, 10);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().getContent()).hasSize(1);
        assertThat(resp.getBody().getTotalElements()).isEqualTo(1);
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
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
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

    // ── upsertAgent ────────────────────────────────────────────────────────────

    @Test
    void upsertAgent_createsNewAgent() {
        WorkflowAgent saved = new WorkflowAgent();
        saved.setName("Research Agent");
        when(workflowService.upsertAgent(
                eq("wf-1"), isNull(), eq("Research Agent"),
                any(WorkflowAgent.AgentRole.class), any(), any(), any(),
                anyInt(), anyDouble(), anyDouble()))
                .thenReturn(saved);

        Map<String, Object> body = Map.of(
                "name",         "Research Agent",
                "role",         "PEER",
                "systemPrompt", "You are a research specialist."
        );

        ResponseEntity<WorkflowAgent> resp = controller.upsertAgent("wf-1", body);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().getName()).isEqualTo("Research Agent");
    }

    @Test
    void upsertAgent_withAgentId_updatesExistingAgent() {
        WorkflowAgent saved = new WorkflowAgent();
        when(workflowService.upsertAgent(
                eq("wf-1"), eq(42L), any(), any(), any(), any(), any(),
                anyInt(), anyDouble(), anyDouble()))
                .thenReturn(saved);

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("id", 42);
        body.put("name", "Updated Agent");
        body.put("role", "MAIN");

        ResponseEntity<WorkflowAgent> resp = controller.upsertAgent("wf-1", body);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(workflowService).upsertAgent(eq("wf-1"), eq(42L), any(), any(), any(), any(), any(),
                anyInt(), anyDouble(), anyDouble());
    }

    @Test
    void upsertAgent_withToolsAndSkills_passesThemCorrectly() {
        WorkflowAgent saved = new WorkflowAgent();
        when(workflowService.upsertAgent(
                eq("wf-1"), any(), any(), any(), any(),
                eq(List.of("BASH", "CURL")), eq(List.of("skill-1")),
                anyInt(), anyDouble(), anyDouble()))
                .thenReturn(saved);

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", "Agent");
        body.put("tools", List.of("BASH", "CURL"));
        body.put("skillIds", List.of("skill-1"));

        controller.upsertAgent("wf-1", body);

        verify(workflowService).upsertAgent(
                eq("wf-1"), any(), any(), any(), any(),
                eq(List.of("BASH", "CURL")), eq(List.of("skill-1")),
                anyInt(), anyDouble(), anyDouble());
    }

    @Test
    void upsertAgent_withPositionCoordinates_passesThemCorrectly() {
        WorkflowAgent saved = new WorkflowAgent();
        when(workflowService.upsertAgent(
                eq("wf-1"), any(), any(), any(), any(), any(), any(),
                eq(2), eq(100.0), eq(200.0)))
                .thenReturn(saved);

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", "Agent");
        body.put("orderIndex", 2);
        body.put("posX", 100.0);
        body.put("posY", 200.0);

        controller.upsertAgent("wf-1", body);

        verify(workflowService).upsertAgent(
                eq("wf-1"), any(), any(), any(), any(), any(), any(),
                eq(2), eq(100.0), eq(200.0));
    }

    // ── createWorkflow — TEAM pattern ──────────────────────────────────────────

    @Test
    void createWorkflow_teamPattern_withSequentialMode() {
        setupRequest("user@example.com");
        Workflow created = makeWorkflow("wf-team");
        when(workflowService.create(anyString(), anyString(), any(OrgContext.class),
                eq(Workflow.AgentPattern.TEAM), eq(Workflow.TeamExecMode.SEQUENTIAL)))
                .thenReturn(created);

        Map<String, Object> body = Map.of(
                "name",         "Team Workflow",
                "agentPattern", "TEAM",
                "teamExecMode", "SEQUENTIAL"
        );

        ResponseEntity<Workflow> resp = controller.createWorkflow(body, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(workflowService).create(anyString(), anyString(), any(OrgContext.class),
                eq(Workflow.AgentPattern.TEAM), eq(Workflow.TeamExecMode.SEQUENTIAL));
    }

    // ── startRun — emailNotify ─────────────────────────────────────────────────

    @Test
    void startRun_emailNotifyTrue_passesFlag() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(runService.startRun(eq("wf-1"), anyString(), anyString(), eq(true))).thenReturn("run-1");

        ResponseEntity<Map<String, String>> resp = controller.startRun("wf-1",
                Map.of("userInput", "Run now", "emailNotify", true), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(runService).startRun(eq("wf-1"), anyString(), anyString(), eq(true));
    }
}
