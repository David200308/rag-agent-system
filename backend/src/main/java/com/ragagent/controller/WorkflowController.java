package com.ragagent.controller;

import com.ragagent.sandbox.SandboxService;
import com.ragagent.workflow.WorkflowRunService;
import com.ragagent.workflow.WorkflowService;
import com.ragagent.workflow.entity.Workflow;
import com.ragagent.workflow.entity.WorkflowAgent;
import com.ragagent.workflow.entity.WorkflowRun;
import com.ragagent.workflow.entity.WorkflowRunLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * REST API for the multi-agent workflow engine.
 *
 * Workflows  — CRUD for workflow definitions + their agents
 * Runs       — trigger a workflow run and stream logs via SSE
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workflow")
@RequiredArgsConstructor
@Tag(name = "Workflow", description = "Multi-agent workflow pipeline endpoints")
public class WorkflowController {

    private final WorkflowService    workflowService;
    private final WorkflowRunService runService;
    private final SandboxService     sandboxService;

    // ── Workflow CRUD ─────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List workflows owned by the authenticated user")
    public ResponseEntity<List<Workflow>> listWorkflows(HttpServletRequest req) {
        String email = resolveEmail(req);
        return ResponseEntity.ok(workflowService.listByOwner(email));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single workflow")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable String id) {
        return workflowService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create a new workflow")
    public ResponseEntity<Workflow> createWorkflow(
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {

        String email   = resolveEmail(req);
        String name    = (String) body.get("name");
        String desc    = (String) body.getOrDefault("description", "");
        Workflow.AgentPattern pattern = Workflow.AgentPattern.valueOf(
                (String) body.getOrDefault("agentPattern", "ORCHESTRATOR"));
        String modeStr = (String) body.get("teamExecMode");
        Workflow.TeamExecMode mode = modeStr != null ? Workflow.TeamExecMode.valueOf(modeStr) : null;

        Workflow created = workflowService.create(name, desc, email, pattern, mode);
        return ResponseEntity.ok(created);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update workflow metadata")
    public ResponseEntity<Workflow> updateWorkflow(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        String email = resolveEmail(req);
        try {
            return ResponseEntity.ok(workflowService.update(id, email, body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a workflow (owner only)")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable String id, HttpServletRequest req) {
        try {
            workflowService.delete(id, resolveEmail(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Agent CRUD ────────────────────────────────────────────────────────────

    @GetMapping("/{workflowId}/agents")
    @Operation(summary = "List agents in a workflow")
    public ResponseEntity<List<WorkflowAgent>> listAgents(@PathVariable String workflowId) {
        return ResponseEntity.ok(workflowService.listAgents(workflowId));
    }

    @PostMapping("/{workflowId}/agents")
    @Operation(summary = "Create or update an agent in a workflow")
    public ResponseEntity<WorkflowAgent> upsertAgent(
            @PathVariable String workflowId,
            @RequestBody Map<String, Object> body) {

        Long agentId = body.get("id") instanceof Number n ? n.longValue() : null;
        String name  = (String) body.get("name");
        WorkflowAgent.AgentRole role = WorkflowAgent.AgentRole.valueOf(
                (String) body.getOrDefault("role", "PEER"));
        String systemPrompt = (String) body.get("systemPrompt");

        @SuppressWarnings("unchecked")
        List<String> tools = body.get("tools") instanceof List<?> l
                ? (List<String>) l : List.of();

        @SuppressWarnings("unchecked")
        List<String> skillIds = body.get("skillIds") instanceof List<?> s
                ? (List<String>) s : List.of();

        int orderIndex = body.get("orderIndex") instanceof Number n ? n.intValue() : 0;
        double posX    = body.get("posX") instanceof Number n ? n.doubleValue() : 0;
        double posY    = body.get("posY") instanceof Number n ? n.doubleValue() : 0;

        WorkflowAgent saved = workflowService.upsertAgent(
                workflowId, agentId, name, role, systemPrompt, tools, skillIds, orderIndex, posX, posY);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{workflowId}/agents/{agentId}")
    @Operation(summary = "Delete an agent from a workflow")
    public ResponseEntity<Void> deleteAgent(
            @PathVariable String workflowId,
            @PathVariable Long agentId) {
        workflowService.deleteAgent(agentId);
        return ResponseEntity.noContent().build();
    }

    // ── Run endpoints ─────────────────────────────────────────────────────────

    @GetMapping("/{workflowId}/runs")
    @Operation(summary = "List runs for a workflow")
    public ResponseEntity<List<WorkflowRun>> listRuns(@PathVariable String workflowId) {
        return ResponseEntity.ok(runService.getRuns(workflowId));
    }

    @PostMapping("/{workflowId}/runs")
    @Operation(summary = "Start a new workflow run")
    public ResponseEntity<Map<String, String>> startRun(
            @PathVariable String workflowId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {

        String userInput = (String) body.get("userInput");
        if (userInput == null || userInput.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userInput required"));
        }
        boolean emailNotify = Boolean.TRUE.equals(body.get("emailNotify"));
        String email = resolveEmail(req);
        String runId = runService.startRun(workflowId, userInput, email, emailNotify);
        return ResponseEntity.ok(Map.of("runId", runId));
    }

    @GetMapping(value = "/runs/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE stream of log events for a run")
    public SseEmitter streamLogs(@PathVariable String runId) {
        return runService.streamLogs(runId);
    }

    @GetMapping("/runs/{runId}/logs")
    @Operation(summary = "Fetch all logs for a completed run")
    public ResponseEntity<List<WorkflowRunLog>> getLogs(@PathVariable String runId) {
        return ResponseEntity.ok(runService.getLogs(runId));
    }

    // ── Sandbox status ────────────────────────────────────────────────────────

    @GetMapping("/sandbox/status")
    @Operation(summary = "Current sandbox queue and capacity status")
    public ResponseEntity<SandboxService.SandboxStatus> sandboxStatus() {
        return ResponseEntity.ok(sandboxService.status());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveEmail(HttpServletRequest req) {
        String email = (String) req.getAttribute("authenticatedEmail");
        return email != null ? email : "anonymous";
    }
}
