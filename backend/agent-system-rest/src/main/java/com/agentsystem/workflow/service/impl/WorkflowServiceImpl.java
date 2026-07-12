package com.agentsystem.workflow.service.impl;

import com.agentsystem.workflow.service.WorkflowService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentsystem.org.OrgContext;
import com.agentsystem.user.entity.User;
import com.agentsystem.user.service.UserAccountService;
import com.agentsystem.workflow.entity.Workflow;
import com.agentsystem.workflow.entity.WorkflowAgent;
import com.agentsystem.workflow.entity.WorkflowEdge;
import com.agentsystem.workflow.entity.WorkflowVersion;
import com.agentsystem.workflow.repository.WorkflowAgentRepository;
import com.agentsystem.workflow.repository.WorkflowEdgeRepository;
import com.agentsystem.workflow.repository.WorkflowRepository;
import com.agentsystem.workflow.repository.WorkflowVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowRepository       workflowRepo;
    private final WorkflowAgentRepository  agentRepo;
    private final WorkflowEdgeRepository   edgeRepo;
    private final WorkflowVersionRepository versionRepo;
    private final ObjectMapper             objectMapper;
    private final UserAccountService       userAccountService;

    // ── Workflow CRUD ─────────────────────────────────────────────────────────

    @Override
    public List<Workflow> list(OrgContext ctx) {
        if (ctx.isTeam()) return workflowRepo.findByOrgIdOrderByUpdatedAtDesc(ctx.orgId());
        return workflowRepo.findByOwnerUuidAndOrgIdIsNullOrderByUpdatedAtDesc(ctx.userUuid());
    }

    /** Backward-compatible alias. Resolves email to a user_uuid internally. */
    @Override
    public List<Workflow> listByOwner(String ownerEmail) {
        return workflowRepo.findByOwnerUuidAndOrgIdIsNullOrderByUpdatedAtDesc(resolveUuid(ownerEmail));
    }

    @Override
    public Optional<Workflow> findById(String id) {
        return workflowRepo.findById(id);
    }

    @Transactional
    @Override
    public Workflow create(String name, String description, OrgContext ctx,
                           Workflow.AgentPattern pattern, Workflow.TeamExecMode teamExecMode) {
        Workflow wf = new Workflow(UUID.randomUUID().toString(), name, ctx.userUuid(), pattern);
        wf.setDescription(description);
        wf.setTeamExecMode(teamExecMode);
        if (ctx.isTeam()) wf.setOrgId(ctx.orgId());
        return workflowRepo.save(wf);
    }

    @Transactional
    @Override
    public Workflow create(String name, String description, String ownerEmail,
                           Workflow.AgentPattern pattern, Workflow.TeamExecMode teamExecMode) {
        return create(name, description, new OrgContext(resolveUuid(ownerEmail), ownerEmail, "PERSONAL", null), pattern, teamExecMode);
    }

    @Transactional
    @Override
    public Workflow update(String id, OrgContext ctx, Map<String, Object> patch) {
        Workflow wf = workflowRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + id));
        // Team mode: any member can edit; personal mode: only owner
        if (!ctx.isTeam() && !ctx.userUuid().equals(wf.getOwnerUuid())) {
            throw new SecurityException("Not the owner");
        }
        if (patch.containsKey("name"))        wf.setName((String) patch.get("name"));
        if (patch.containsKey("description")) wf.setDescription((String) patch.get("description"));
        if (patch.containsKey("agentPattern")) {
            wf.setAgentPattern(Workflow.AgentPattern.valueOf((String) patch.get("agentPattern")));
        }
        if (patch.containsKey("teamExecMode")) {
            String v = (String) patch.get("teamExecMode");
            wf.setTeamExecMode(v == null ? null : Workflow.TeamExecMode.valueOf(v));
        }
        if (patch.containsKey("selectedModel")) {
            String m = (String) patch.get("selectedModel");
            wf.setSelectedModel(m == null || m.isBlank() ? null : m);
        }
        return workflowRepo.save(wf);
    }

    @Transactional
    @Override
    public Workflow update(String id, String ownerEmail, Map<String, Object> patch) {
        return update(id, new OrgContext(resolveUuid(ownerEmail), ownerEmail, "PERSONAL", null), patch);
    }

    @Transactional
    @Override
    public void delete(String id, OrgContext ctx) {
        Workflow wf = workflowRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));
        if (!ctx.isTeam() && !ctx.userUuid().equals(wf.getOwnerUuid())) {
            throw new SecurityException("Not the owner");
        }
        workflowRepo.delete(wf);
    }

    @Transactional
    @Override
    public void delete(String id, String ownerEmail) {
        delete(id, new OrgContext(resolveUuid(ownerEmail), ownerEmail, "PERSONAL", null));
    }

    // ── Agent CRUD ────────────────────────────────────────────────────────────

    @Override
    public List<WorkflowAgent> listAgents(String workflowId) {
        return agentRepo.findByWorkflowIdOrderByOrderIndex(workflowId);
    }

    @Transactional
    @Override
    public WorkflowAgent upsertAgent(String workflowId, Long agentId, String name,
                                     WorkflowAgent.AgentRole role, String systemPrompt,
                                     List<String> tools, List<String> skillIds,
                                     int orderIndex, double posX, double posY,
                                     WorkflowAgent.NodeKind nodeKind, String conditionExpr,
                                     String outputSchemaJson) {
        WorkflowAgent agent = agentId != null
                ? agentRepo.findById(agentId).orElse(new WorkflowAgent())
                : new WorkflowAgent();

        agent.setWorkflowId(workflowId);
        agent.setName(name);
        agent.setRole(role);
        agent.setNodeKind(nodeKind != null ? nodeKind : WorkflowAgent.NodeKind.AGENT);
        agent.setConditionExpr(conditionExpr);
        agent.setOutputSchemaJson(outputSchemaJson);
        agent.setSystemPrompt(systemPrompt);
        agent.setOrderIndex(orderIndex);
        agent.setPosX(posX);
        agent.setPosY(posY);

        try {
            agent.setToolsJson(objectMapper.writeValueAsString(tools));
        } catch (JsonProcessingException e) {
            agent.setToolsJson("[]");
        }
        try {
            agent.setSkillIdsJson(objectMapper.writeValueAsString(skillIds != null ? skillIds : List.of()));
        } catch (JsonProcessingException e) {
            agent.setSkillIdsJson("[]");
        }

        return agentRepo.save(agent);
    }

    @Transactional
    @Override
    public void deleteAgent(Long agentId) {
        agentRepo.deleteById(agentId);
    }

    // ── Edge CRUD (GRAPH pattern) ────────────────────────────────────────────

    @Override
    public List<WorkflowEdge> listEdges(String workflowId) {
        return edgeRepo.findByWorkflowId(workflowId);
    }

    @Transactional
    @Override
    public WorkflowEdge upsertEdge(String workflowId, Long sourceNodeId, Long targetNodeId, String branchLabel) {
        WorkflowEdge edge = new WorkflowEdge();
        edge.setWorkflowId(workflowId);
        edge.setSourceNodeId(sourceNodeId);
        edge.setTargetNodeId(targetNodeId);
        edge.setBranchLabel(branchLabel);
        return edgeRepo.save(edge);
    }

    @Transactional
    @Override
    public void deleteEdge(Long edgeId) {
        edgeRepo.deleteById(edgeId);
    }

    // ── Versions ──────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public WorkflowVersion createVersion(String workflowId, String label) {
        Workflow wf = workflowRepo.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));
        List<WorkflowAgent> agentList = agentRepo.findByWorkflowIdOrderByOrderIndex(workflowId);
        List<WorkflowEdge> edgeList = edgeRepo.findByWorkflowId(workflowId);

        Map<Long, Integer> indexById = new HashMap<>();
        for (int i = 0; i < agentList.size(); i++) indexById.put(agentList.get(i).getId(), i);

        List<WorkflowSnapshot.SnapshotAgent> snapAgents = agentList.stream()
                .map(a -> new WorkflowSnapshot.SnapshotAgent(
                        a.getName(), a.getRole(), a.getNodeKind(), a.getConditionExpr(), a.getOutputSchemaJson(),
                        a.getSystemPrompt(), a.getToolsJson(), a.getSkillIdsJson(),
                        a.getOrderIndex(), a.getPosX(), a.getPosY()))
                .toList();

        List<WorkflowSnapshot.SnapshotEdge> snapEdges = edgeList.stream()
                .filter(e -> indexById.containsKey(e.getSourceNodeId()) && indexById.containsKey(e.getTargetNodeId()))
                .map(e -> new WorkflowSnapshot.SnapshotEdge(
                        indexById.get(e.getSourceNodeId()), indexById.get(e.getTargetNodeId()), e.getBranchLabel()))
                .toList();

        WorkflowSnapshot snapshot = new WorkflowSnapshot(wf.getAgentPattern(), wf.getTeamExecMode(), snapAgents, snapEdges);

        int nextVersion = versionRepo.findTopByWorkflowIdOrderByVersionNumberDesc(workflowId)
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        WorkflowVersion version = new WorkflowVersion();
        version.setWorkflowId(workflowId);
        version.setVersionNumber(nextVersion);
        version.setLabel(label != null && !label.isBlank() ? label : null);
        try {
            version.setSnapshotJson(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize workflow snapshot", e);
        }
        return versionRepo.save(version);
    }

    @Override
    public List<WorkflowVersion> listVersions(String workflowId) {
        return versionRepo.findByWorkflowIdOrderByVersionNumberDesc(workflowId);
    }

    @Transactional
    @Override
    public WorkflowVersion restoreVersion(String workflowId, int versionNumber) {
        WorkflowVersion target = versionRepo.findByWorkflowIdAndVersionNumber(workflowId, versionNumber)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionNumber));

        WorkflowSnapshot snapshot;
        try {
            snapshot = objectMapper.readValue(target.getSnapshotJson(), WorkflowSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize workflow snapshot", e);
        }

        Workflow wf = workflowRepo.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));
        wf.setAgentPattern(snapshot.agentPattern());
        wf.setTeamExecMode(snapshot.teamExecMode());
        workflowRepo.save(wf);

        // Replace current agents wholesale — edges cascade-delete with their source/target agents
        agentRepo.deleteAll(agentRepo.findByWorkflowIdOrderByOrderIndex(workflowId));

        List<WorkflowAgent> created = new ArrayList<>();
        for (WorkflowSnapshot.SnapshotAgent sa : snapshot.agents()) {
            WorkflowAgent agent = new WorkflowAgent();
            agent.setWorkflowId(workflowId);
            agent.setName(sa.name());
            agent.setRole(sa.role());
            agent.setNodeKind(sa.nodeKind() != null ? sa.nodeKind() : WorkflowAgent.NodeKind.AGENT);
            agent.setConditionExpr(sa.conditionExpr());
            agent.setOutputSchemaJson(sa.outputSchemaJson());
            agent.setSystemPrompt(sa.systemPrompt());
            agent.setToolsJson(sa.toolsJson() != null ? sa.toolsJson() : "[]");
            agent.setSkillIdsJson(sa.skillIdsJson() != null ? sa.skillIdsJson() : "[]");
            agent.setOrderIndex(sa.orderIndex());
            agent.setPosX(sa.posX());
            agent.setPosY(sa.posY());
            created.add(agentRepo.save(agent));
        }

        for (WorkflowSnapshot.SnapshotEdge se : snapshot.edges()) {
            if (se.sourceIndex() < 0 || se.sourceIndex() >= created.size()
                    || se.targetIndex() < 0 || se.targetIndex() >= created.size()) continue;
            WorkflowEdge edge = new WorkflowEdge();
            edge.setWorkflowId(workflowId);
            edge.setSourceNodeId(created.get(se.sourceIndex()).getId());
            edge.setTargetNodeId(created.get(se.targetIndex()).getId());
            edge.setBranchLabel(se.branchLabel());
            edgeRepo.save(edge);
        }

        // Record the restore itself as a new version so history is append-only
        return createVersion(workflowId, "Restored from v" + versionNumber);
    }

    @Override
    public Optional<Integer> latestVersionNumber(String workflowId) {
        return versionRepo.findTopByWorkflowIdOrderByVersionNumberDesc(workflowId)
                .map(WorkflowVersion::getVersionNumber);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> parseTools(WorkflowAgent agent) {
        try {
            return objectMapper.readValue(
                    agent.getToolsJson() != null ? agent.getToolsJson() : "[]",
                    List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> parseSkillIds(WorkflowAgent agent) {
        try {
            return objectMapper.readValue(
                    agent.getSkillIdsJson() != null ? agent.getSkillIdsJson() : "[]",
                    List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /** Resolves an email to its user_uuid, or null if no such email is a registered user. */
    private String resolveUuid(String email) {
        if (email == null || email.isBlank()) return null;
        return userAccountService.findByEmail(email).map(User::getUuid).orElse(null);
    }
}
