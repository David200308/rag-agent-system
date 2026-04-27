package com.ragagent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.workflow.entity.Workflow;
import com.ragagent.workflow.entity.WorkflowAgent;
import com.ragagent.workflow.repository.WorkflowAgentRepository;
import com.ragagent.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock WorkflowRepository      workflowRepo;
    @Mock WorkflowAgentRepository agentRepo;

    WorkflowService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowService(workflowRepo, agentRepo, new ObjectMapper());
    }

    // ── listByOwner ───────────────────────────────────────────────────────────

    @Test
    void listByOwner_delegatesToRepository() {
        Workflow wf = new Workflow("w1", "My Flow", "owner@test.com", Workflow.AgentPattern.ORCHESTRATOR);
        when(workflowRepo.findByOwnerEmailOrderByUpdatedAtDesc("owner@test.com"))
                .thenReturn(List.of(wf));

        List<Workflow> result = service.listByOwner("owner@test.com");

        assertThat(result).containsExactly(wf);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_existing_returnsOptionalWithWorkflow() {
        Workflow wf = new Workflow("w1", "Flow", "owner@test.com", Workflow.AgentPattern.TEAM);
        when(workflowRepo.findById("w1")).thenReturn(Optional.of(wf));

        assertThat(service.findById("w1")).contains(wf);
    }

    @Test
    void findById_missing_returnsEmpty() {
        when(workflowRepo.findById("ghost")).thenReturn(Optional.empty());

        assertThat(service.findById("ghost")).isEmpty();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_savesWorkflowWithGeneratedId() {
        when(workflowRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<Workflow> captor = ArgumentCaptor.forClass(Workflow.class);

        Workflow result = service.create("My Flow", "desc", "owner@test.com",
                Workflow.AgentPattern.ORCHESTRATOR, null);

        verify(workflowRepo).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("My Flow");
        assertThat(captor.getValue().getOwnerEmail()).isEqualTo("owner@test.com");
        assertThat(captor.getValue().getId()).isNotBlank();
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_ownerCanChangeName() {
        Workflow wf = new Workflow("w1", "Old", "owner@test.com", Workflow.AgentPattern.ORCHESTRATOR);
        when(workflowRepo.findById("w1")).thenReturn(Optional.of(wf));
        when(workflowRepo.save(wf)).thenReturn(wf);

        service.update("w1", "owner@test.com", Map.of("name", "New Name"));

        assertThat(wf.getName()).isEqualTo("New Name");
    }

    @Test
    void update_nonOwnerThrowsSecurityException() {
        Workflow wf = new Workflow("w1", "Flow", "owner@test.com", Workflow.AgentPattern.ORCHESTRATOR);
        when(workflowRepo.findById("w1")).thenReturn(Optional.of(wf));

        assertThatThrownBy(() -> service.update("w1", "other@test.com", Map.of("name", "Hack")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Not the owner");
    }

    @Test
    void update_workflowNotFound_throwsIllegalArgument() {
        when(workflowRepo.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("ghost", "owner@test.com", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_ownerDeletesSuccessfully() {
        Workflow wf = new Workflow("w1", "Flow", "owner@test.com", Workflow.AgentPattern.ORCHESTRATOR);
        when(workflowRepo.findById("w1")).thenReturn(Optional.of(wf));

        service.delete("w1", "owner@test.com");

        verify(workflowRepo).delete(wf);
    }

    @Test
    void delete_nonOwnerThrowsSecurityException() {
        Workflow wf = new Workflow("w1", "Flow", "owner@test.com", Workflow.AgentPattern.ORCHESTRATOR);
        when(workflowRepo.findById("w1")).thenReturn(Optional.of(wf));

        assertThatThrownBy(() -> service.delete("w1", "intruder@test.com"))
                .isInstanceOf(SecurityException.class);

        verify(workflowRepo, never()).delete(any());
    }

    // ── upsertAgent ───────────────────────────────────────────────────────────

    @Test
    void upsertAgent_newAgent_savesWithAllFields() {
        when(agentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<WorkflowAgent> captor = ArgumentCaptor.forClass(WorkflowAgent.class);

        service.upsertAgent("w1", null, "Researcher", WorkflowAgent.AgentRole.MAIN,
                "You are a researcher", List.of("web_search"), List.of("skill-1"), 0, 100.0, 200.0);

        verify(agentRepo).save(captor.capture());
        WorkflowAgent saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Researcher");
        assertThat(saved.getWorkflowId()).isEqualTo("w1");
        assertThat(saved.getOrderIndex()).isEqualTo(0);
    }

    @Test
    void upsertAgent_existingAgentId_loadsAndUpdates() {
        WorkflowAgent existing = new WorkflowAgent();
        existing.setName("Old Name");
        when(agentRepo.findById(99L)).thenReturn(Optional.of(existing));
        when(agentRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.upsertAgent("w1", 99L, "New Name", WorkflowAgent.AgentRole.SUB,
                null, List.of(), null, 1, 0.0, 0.0);

        assertThat(existing.getName()).isEqualTo("New Name");
    }

    // ── listAgents ────────────────────────────────────────────────────────────

    @Test
    void listAgents_delegatesToRepository() {
        WorkflowAgent a = new WorkflowAgent();
        when(agentRepo.findByWorkflowIdOrderByOrderIndex("w1")).thenReturn(List.of(a));

        assertThat(service.listAgents("w1")).containsExactly(a);
    }

    // ── parseTools ────────────────────────────────────────────────────────────

    @Test
    void parseTools_validJson_returnsList() {
        WorkflowAgent agent = new WorkflowAgent();
        agent.setToolsJson("[\"web_search\",\"calculator\"]");

        List<String> tools = service.parseTools(agent);

        assertThat(tools).containsExactly("web_search", "calculator");
    }

    @Test
    void parseTools_nullJson_returnsEmptyList() {
        WorkflowAgent agent = new WorkflowAgent();
        agent.setToolsJson(null);

        assertThat(service.parseTools(agent)).isEmpty();
    }

    @Test
    void parseTools_invalidJson_returnsEmptyList() {
        WorkflowAgent agent = new WorkflowAgent();
        agent.setToolsJson("{broken json}");

        assertThat(service.parseTools(agent)).isEmpty();
    }

    // ── parseSkillIds ─────────────────────────────────────────────────────────

    @Test
    void parseSkillIds_validJson_returnsList() {
        WorkflowAgent agent = new WorkflowAgent();
        agent.setSkillIdsJson("[\"skill-abc\",\"skill-xyz\"]");

        assertThat(service.parseSkillIds(agent)).containsExactly("skill-abc", "skill-xyz");
    }

    @Test
    void parseSkillIds_nullJson_returnsEmptyList() {
        WorkflowAgent agent = new WorkflowAgent();
        agent.setSkillIdsJson(null);

        assertThat(service.parseSkillIds(agent)).isEmpty();
    }
}
