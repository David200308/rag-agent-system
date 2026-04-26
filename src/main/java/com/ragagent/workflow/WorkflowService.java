package com.ragagent.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.workflow.entity.Workflow;
import com.ragagent.workflow.entity.WorkflowAgent;
import com.ragagent.workflow.repository.WorkflowAgentRepository;
import com.ragagent.workflow.repository.WorkflowRepository;
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
public class WorkflowService {

    private final WorkflowRepository       workflowRepo;
    private final WorkflowAgentRepository  agentRepo;
    private final ObjectMapper             objectMapper;

    // ── Workflow CRUD ─────────────────────────────────────────────────────────

    public List<Workflow> listByOwner(String ownerEmail) {
        return workflowRepo.findByOwnerEmailOrderByUpdatedAtDesc(ownerEmail);
    }

    public Optional<Workflow> findById(String id) {
        return workflowRepo.findById(id);
    }

    @Transactional
    public Workflow create(String name, String description, String ownerEmail,
                           Workflow.AgentPattern pattern, Workflow.TeamExecMode teamExecMode) {
        Workflow wf = new Workflow(UUID.randomUUID().toString(), name, ownerEmail, pattern);
        wf.setDescription(description);
        wf.setTeamExecMode(teamExecMode);
        return workflowRepo.save(wf);
    }

    @Transactional
    public Workflow update(String id, String ownerEmail, Map<String, Object> patch) {
        Workflow wf = workflowRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + id));
        if (!ownerEmail.equals(wf.getOwnerEmail())) throw new SecurityException("Not the owner");

        if (patch.containsKey("name"))        wf.setName((String) patch.get("name"));
        if (patch.containsKey("description")) wf.setDescription((String) patch.get("description"));
        if (patch.containsKey("agentPattern")) {
            wf.setAgentPattern(Workflow.AgentPattern.valueOf((String) patch.get("agentPattern")));
        }
        if (patch.containsKey("teamExecMode")) {
            String v = (String) patch.get("teamExecMode");
            wf.setTeamExecMode(v == null ? null : Workflow.TeamExecMode.valueOf(v));
        }
        return workflowRepo.save(wf);
    }

    @Transactional
    public void delete(String id, String ownerEmail) {
        Workflow wf = workflowRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));
        if (!ownerEmail.equals(wf.getOwnerEmail())) throw new SecurityException("Not the owner");
        workflowRepo.delete(wf);
    }

    // ── Agent CRUD ────────────────────────────────────────────────────────────

    public List<WorkflowAgent> listAgents(String workflowId) {
        return agentRepo.findByWorkflowIdOrderByOrderIndex(workflowId);
    }

    @Transactional
    public WorkflowAgent upsertAgent(String workflowId, Long agentId, String name,
                                     WorkflowAgent.AgentRole role, String systemPrompt,
                                     List<String> tools, int orderIndex,
                                     double posX, double posY) {
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

        return agentRepo.save(agent);
    }

    @Transactional
    public void deleteAgent(Long agentId) {
        agentRepo.deleteById(agentId);
    }

    @SuppressWarnings("unchecked")
    public List<String> parseTools(WorkflowAgent agent) {
        try {
            return objectMapper.readValue(
                    agent.getToolsJson() != null ? agent.getToolsJson() : "[]",
                    List.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}
