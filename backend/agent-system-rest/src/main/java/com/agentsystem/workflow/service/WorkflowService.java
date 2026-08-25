package com.agentsystem.workflow.service;

import com.agentsystem.org.OrgContext;
import com.agentsystem.workflow.entity.Workflow;
import com.agentsystem.workflow.entity.WorkflowAgent;
import com.agentsystem.workflow.entity.WorkflowEdge;
import com.agentsystem.workflow.entity.WorkflowVersion;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface WorkflowService {

    // ── Workflow CRUD ─────────────────────────────────────────────────────────

    List<Workflow> list(OrgContext ctx);

    /** Backward-compatible alias. */
    List<Workflow> listByOwner(String ownerEmail);

    Optional<Workflow> findById(String id);

    Workflow create(String name, String description, OrgContext ctx,
                     Workflow.AgentPattern pattern, Workflow.TeamExecMode teamExecMode);

    Workflow create(String name, String description, String ownerEmail,
                     Workflow.AgentPattern pattern, Workflow.TeamExecMode teamExecMode);

    Workflow update(String id, OrgContext ctx, Map<String, Object> patch);

    Workflow update(String id, String ownerEmail, Map<String, Object> patch);

    void delete(String id, OrgContext ctx);

    void delete(String id, String ownerEmail);

    // ── Agent CRUD ────────────────────────────────────────────────────────────

    List<WorkflowAgent> listAgents(String workflowId);

    WorkflowAgent upsertAgent(String workflowId, Long agentId, String name,
                               WorkflowAgent.AgentRole role, String systemPrompt,
                               List<String> tools, List<String> skillIds,
                               int orderIndex, double posX, double posY,
                               WorkflowAgent.NodeKind nodeKind, String conditionExpr,
                               String outputSchemaJson);

    void deleteAgent(Long agentId);

    List<String> parseTools(WorkflowAgent agent);

    List<String> parseSkillIds(WorkflowAgent agent);

    // ── Edge CRUD (GRAPH pattern) ────────────────────────────────────────────

    List<WorkflowEdge> listEdges(String workflowId);

    WorkflowEdge upsertEdge(String workflowId, Long sourceNodeId, Long targetNodeId, String branchLabel);

    void deleteEdge(Long edgeId);

    // ── Versions ──────────────────────────────────────────────────────────────

    /** Snapshots the workflow's current pattern + agents + edges as a new version. */
    WorkflowVersion createVersion(String workflowId, String label);

    List<WorkflowVersion> listVersions(String workflowId);

    /** Replaces the workflow's current agents/edges with a saved snapshot, then records the restore itself as a new version. */
    WorkflowVersion restoreVersion(String workflowId, int versionNumber);

    /** Latest saved version number for a workflow, or empty if none has ever been saved. */
    Optional<Integer> latestVersionNumber(String workflowId);
}
