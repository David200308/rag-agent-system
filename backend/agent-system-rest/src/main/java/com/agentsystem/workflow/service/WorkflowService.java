package com.agentsystem.workflow.service;

import com.agentsystem.org.OrgContext;
import com.agentsystem.workflow.entity.Workflow;
import com.agentsystem.workflow.entity.WorkflowAgent;

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
                               int orderIndex, double posX, double posY);

    void deleteAgent(Long agentId);

    List<String> parseTools(WorkflowAgent agent);

    List<String> parseSkillIds(WorkflowAgent agent);
}
