package com.agentsystem.workflow.service.impl;

import com.agentsystem.workflow.service.WorkflowService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentsystem.org.OrgContext;
import com.agentsystem.workflow.entity.Workflow;
import com.agentsystem.workflow.entity.WorkflowAgent;
import com.agentsystem.workflow.repository.WorkflowAgentRepository;
import com.agentsystem.workflow.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ObjectMapper             objectMapper;

    // ── Workflow CRUD ─────────────────────────────────────────────────────────

    @Override
    public List<Workflow> list(OrgContext ctx) {
        if (ctx.isTeam()) return workflowRepo.findByOrgIdOrderByUpdatedAtDesc(ctx.orgId());
        return workflowRepo.findByOwnerEmailAndOrgIdIsNullOrderByUpdatedAtDesc(ctx.email());
    }

    /** Backward-compatible alias. */
    @Override
    public List<Workflow> listByOwner(String ownerEmail) {
        return workflowRepo.findByOwnerEmailAndOrgIdIsNullOrderByUpdatedAtDesc(ownerEmail);
    }

    @Override
    public Optional<Workflow> findById(String id) {
        return workflowRepo.findById(id);
    }

    @Transactional
    @Override
    public Workflow create(String name, String description, OrgContext ctx,
                           Workflow.AgentPattern pattern, Workflow.TeamExecMode teamExecMode) {
        Workflow wf = new Workflow(UUID.randomUUID().toString(), name, ctx.email(), pattern);
        wf.setDescription(description);
        wf.setTeamExecMode(teamExecMode);
        if (ctx.isTeam()) wf.setOrgId(ctx.orgId());
        return workflowRepo.save(wf);
    }

    @Transactional
    @Override
    public Workflow create(String name, String description, String ownerEmail,
                           Workflow.AgentPattern pattern, Workflow.TeamExecMode teamExecMode) {
        return create(name, description, new OrgContext(ownerEmail, "PERSONAL", null), pattern, teamExecMode);
    }

    @Transactional
    @Override
    public Workflow update(String id, OrgContext ctx, Map<String, Object> patch) {
        Workflow wf = workflowRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + id));
        // Team mode: any member can edit; personal mode: only owner
        if (!ctx.isTeam() && !ctx.email().equals(wf.getOwnerEmail())) {
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
        return update(id, new OrgContext(ownerEmail, "PERSONAL", null), patch);
    }

    @Transactional
    @Override
    public void delete(String id, OrgContext ctx) {
        Workflow wf = workflowRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));
        if (!ctx.isTeam() && !ctx.email().equals(wf.getOwnerEmail())) {
            throw new SecurityException("Not the owner");
        }
        workflowRepo.delete(wf);
    }

    @Transactional
    @Override
    public void delete(String id, String ownerEmail) {
        delete(id, new OrgContext(ownerEmail, "PERSONAL", null));
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
                                     int orderIndex, double posX, double posY) {
        WorkflowAgent agent = agentId != null
                ? agentRepo.findById(agentId).orElse(new WorkflowAgent())
                : new WorkflowAgent();

        agent.setWorkflowId(workflowId);
        agent.setName(name);
        agent.setRole(role);
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
}
