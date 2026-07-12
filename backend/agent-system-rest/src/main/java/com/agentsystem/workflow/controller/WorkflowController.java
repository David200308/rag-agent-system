package com.agentsystem.workflow.controller;

import com.agentsystem.org.OrgContext;
import com.agentsystem.sandbox.service.SandboxService;
import com.agentsystem.workflow.service.WorkflowRunService;
import com.agentsystem.workflow.service.WorkflowService;
import com.agentsystem.workflow.entity.Workflow;
import com.agentsystem.workflow.entity.WorkflowAgent;
import com.agentsystem.workflow.entity.WorkflowEdge;
import com.agentsystem.workflow.entity.WorkflowRun;
import com.agentsystem.workflow.entity.WorkflowRunLog;
import com.agentsystem.workflow.entity.WorkflowVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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
    @Operation(summary = "List workflows for the authenticated user or org")
    public ResponseEntity<List<Workflow>> listWorkflows(HttpServletRequest req) {
        return ResponseEntity.ok(workflowService.list(OrgContext.from(req)));
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

        OrgContext ctx = OrgContext.from(req);
        String name    = (String) body.get("name");
        String desc    = (String) body.getOrDefault("description", "");
        Workflow.AgentPattern pattern = Workflow.AgentPattern.valueOf(
                (String) body.getOrDefault("agentPattern", "ORCHESTRATOR"));
        String modeStr = (String) body.get("teamExecMode");
        Workflow.TeamExecMode execMode = modeStr != null ? Workflow.TeamExecMode.valueOf(modeStr) : null;

        Workflow created = workflowService.create(name, desc, ctx, pattern, execMode);
        return ResponseEntity.ok(created);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update workflow metadata")
    public ResponseEntity<Workflow> updateWorkflow(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(workflowService.update(id, OrgContext.from(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a workflow (owner or org member only)")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable String id, HttpServletRequest req) {
        try {
            workflowService.delete(id, OrgContext.from(req));
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

        WorkflowAgent.NodeKind nodeKind = WorkflowAgent.NodeKind.valueOf(
                (String) body.getOrDefault("nodeKind", "AGENT"));
        String conditionExpr   = (String) body.get("conditionExpr");
        String outputSchemaJson = (String) body.get("outputSchemaJson");

        WorkflowAgent saved = workflowService.upsertAgent(
                workflowId, agentId, name, role, systemPrompt, tools, skillIds, orderIndex, posX, posY,
                nodeKind, conditionExpr, outputSchemaJson);
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

    // ── Edge CRUD (GRAPH pattern) ─────────────────────────────────────────────

    @GetMapping("/{workflowId}/edges")
    @Operation(summary = "List explicit node edges for a GRAPH-pattern workflow")
    public ResponseEntity<List<WorkflowEdge>> listEdges(@PathVariable String workflowId) {
        return ResponseEntity.ok(workflowService.listEdges(workflowId));
    }

    @PostMapping("/{workflowId}/edges")
    @Operation(summary = "Create an edge between two nodes")
    public ResponseEntity<WorkflowEdge> createEdge(
            @PathVariable String workflowId,
            @RequestBody Map<String, Object> body) {

        Long sourceNodeId = body.get("sourceNodeId") instanceof Number n ? n.longValue() : null;
        Long targetNodeId = body.get("targetNodeId") instanceof Number n ? n.longValue() : null;
        String branchLabel = (String) body.get("branchLabel");

        if (sourceNodeId == null || targetNodeId == null) {
            return ResponseEntity.badRequest().build();
        }

        WorkflowEdge saved = workflowService.upsertEdge(workflowId, sourceNodeId, targetNodeId, branchLabel);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{workflowId}/edges/{edgeId}")
    @Operation(summary = "Delete an edge")
    public ResponseEntity<Void> deleteEdge(
            @PathVariable String workflowId,
            @PathVariable Long edgeId) {
        workflowService.deleteEdge(edgeId);
        return ResponseEntity.noContent().build();
    }

    // ── Versions ──────────────────────────────────────────────────────────────

    @GetMapping("/{workflowId}/versions")
    @Operation(summary = "List saved versions of a workflow, newest first")
    public ResponseEntity<List<WorkflowVersion>> listVersions(@PathVariable String workflowId) {
        return ResponseEntity.ok(workflowService.listVersions(workflowId));
    }

    @PostMapping("/{workflowId}/versions")
    @Operation(summary = "Snapshot the workflow's current pattern/agents/edges as a new version")
    public ResponseEntity<WorkflowVersion> createVersion(
            @PathVariable String workflowId,
            @RequestBody Map<String, Object> body) {
        String label = (String) body.get("label");
        return ResponseEntity.ok(workflowService.createVersion(workflowId, label));
    }

    @PostMapping("/{workflowId}/versions/{versionNumber}/restore")
    @Operation(summary = "Restore a saved version — replaces current agents/edges and records the restore as a new version")
    public ResponseEntity<WorkflowVersion> restoreVersion(
            @PathVariable String workflowId,
            @PathVariable int versionNumber) {
        try {
            return ResponseEntity.ok(workflowService.restoreVersion(workflowId, versionNumber));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Run endpoints ─────────────────────────────────────────────────────────

    @GetMapping("/{workflowId}/runs")
    @Operation(summary = "List runs for a workflow")
    public ResponseEntity<Page<WorkflowRun>> listRuns(
            @PathVariable String workflowId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(runService.getRuns(workflowId, page, size));
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
        OrgContext ctx = OrgContext.from(req);
        String runId = runService.startRun(workflowId, userInput, ctx.email(), emailNotify);
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

}
