package com.ragagent.org;

import com.ragagent.knowledge.KnowledgeSourceService;
import com.ragagent.knowledge.entity.KnowledgeSource;
import com.ragagent.skill.SkillService;
import com.ragagent.skill.entity.Skill;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamControllerTest {

    @Mock OrganizationService    orgService;
    @Mock KnowledgeSourceService knowledgeSourceService;
    @Mock SkillService           skillService;
    @Mock HttpServletRequest     request;

    @InjectMocks TeamController controller;

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stubTeamRequest(String email) {
        when(request.getAttribute("authenticatedEmail")).thenReturn(email);
        when(request.getAttribute("authenticatedMode")).thenReturn("TEAM");
        when(request.getAttribute("authenticatedOrgId")).thenReturn("skyproton");
    }

    private void stubPersonalRequest(String email) {
        when(request.getAttribute("authenticatedEmail")).thenReturn(email);
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);
    }

    private KnowledgeSource pendingKb(long id, String source) {
        KnowledgeSource ks = new KnowledgeSource(source, source, null, 3, "member@test.com", "skyproton");
        ks.setStatus("PENDING");
        return ks;
    }

    private Skill pendingSkill(String id) {
        Skill s = new Skill(id, "member@test.com", "Draft Tool", "d.py", "python", 50, "code");
        s.setOrgId("skyproton");
        s.setStatus("PENDING");
        return s;
    }

    // ── listApprovals ─────────────────────────────────────────────────────────

    @Test
    void listApprovals_ownerReturns200WithPendingItems() {
        stubTeamRequest("owner@test.com");
        doNothing().when(orgService).requireOwner("skyproton", "owner@test.com");
        KnowledgeSource kb = pendingKb(1L, "doc.pdf");
        Skill skill = pendingSkill("skill-1");
        when(knowledgeSourceService.listPendingByOrg("skyproton")).thenReturn(List.of(kb));
        when(skillService.listPendingByOrg("skyproton")).thenReturn(List.of(skill));

        ResponseEntity<?> resp = controller.listApprovals(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body).containsKey("knowledge");
        assertThat(body).containsKey("skills");
    }

    @Test
    void listApprovals_personalMode_returns403() {
        stubPersonalRequest("owner@test.com");

        ResponseEntity<?> resp = controller.listApprovals(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(knowledgeSourceService, skillService);
    }

    @Test
    void listApprovals_memberRole_returns403() {
        stubTeamRequest("member@test.com");
        doThrow(new SecurityException("Only the organization owner can perform this action."))
                .when(orgService).requireOwner("skyproton", "member@test.com");

        ResponseEntity<?> resp = controller.listApprovals(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(knowledgeSourceService, skillService);
    }

    @Test
    void listApprovals_emptyQueue_returnsEmptyLists() {
        stubTeamRequest("owner@test.com");
        doNothing().when(orgService).requireOwner("skyproton", "owner@test.com");
        when(knowledgeSourceService.listPendingByOrg("skyproton")).thenReturn(List.of());
        when(skillService.listPendingByOrg("skyproton")).thenReturn(List.of());

        ResponseEntity<?> resp = controller.listApprovals(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat((List<?>) body.get("knowledge")).isEmpty();
        assertThat((List<?>) body.get("skills")).isEmpty();
    }

    // ── approveKnowledge ──────────────────────────────────────────────────────

    @Test
    void approveKnowledge_owner_callsServiceAndReturns200() {
        stubTeamRequest("owner@test.com");
        doNothing().when(orgService).requireOwner("skyproton", "owner@test.com");
        KnowledgeSource approved = pendingKb(1L, "doc.pdf");
        approved.setStatus("APPROVED");
        when(knowledgeSourceService.approve(1L)).thenReturn(approved);

        ResponseEntity<?> resp = controller.approveKnowledge(1L, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(knowledgeSourceService).approve(1L);
    }

    @Test
    void approveKnowledge_personalMode_returns403() {
        stubPersonalRequest("owner@test.com");

        ResponseEntity<?> resp = controller.approveKnowledge(1L, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(knowledgeSourceService);
    }

    @Test
    void approveKnowledge_memberRole_returns403() {
        stubTeamRequest("member@test.com");
        doThrow(new SecurityException("Only the organization owner can perform this action."))
                .when(orgService).requireOwner("skyproton", "member@test.com");

        ResponseEntity<?> resp = controller.approveKnowledge(1L, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(knowledgeSourceService);
    }

    @Test
    void approveKnowledge_notFound_returns400() {
        stubTeamRequest("owner@test.com");
        doNothing().when(orgService).requireOwner("skyproton", "owner@test.com");
        when(knowledgeSourceService.approve(99L))
                .thenThrow(new IllegalArgumentException("Knowledge source not found: 99"));

        ResponseEntity<?> resp = controller.approveKnowledge(99L, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ── rejectKnowledge ───────────────────────────────────────────────────────

    @Test
    void rejectKnowledge_owner_callsServiceAndReturns200() {
        stubTeamRequest("owner@test.com");
        doNothing().when(orgService).requireOwner("skyproton", "owner@test.com");
        doNothing().when(knowledgeSourceService).reject(1L);

        ResponseEntity<?> resp = controller.rejectKnowledge(1L, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(knowledgeSourceService).reject(1L);
    }

    @Test
    void rejectKnowledge_memberRole_returns403() {
        stubTeamRequest("member@test.com");
        doThrow(new SecurityException("Only the organization owner can perform this action."))
                .when(orgService).requireOwner("skyproton", "member@test.com");

        ResponseEntity<?> resp = controller.rejectKnowledge(1L, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(knowledgeSourceService);
    }

    @Test
    void rejectKnowledge_notFound_returns400() {
        stubTeamRequest("owner@test.com");
        doNothing().when(orgService).requireOwner("skyproton", "owner@test.com");
        doThrow(new IllegalArgumentException("Knowledge source not found: 99"))
                .when(knowledgeSourceService).reject(99L);

        ResponseEntity<?> resp = controller.rejectKnowledge(99L, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ── approveSkill ──────────────────────────────────────────────────────────

    @Test
    void approveSkill_owner_callsServiceAndReturns200() {
        stubTeamRequest("owner@test.com");
        doNothing().when(orgService).requireOwner("skyproton", "owner@test.com");
        Skill approved = pendingSkill("skill-1");
        approved.setStatus("APPROVED");
        when(skillService.approve("skill-1")).thenReturn(approved);

        ResponseEntity<?> resp = controller.approveSkill("skill-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(skillService).approve("skill-1");
    }

    @Test
    void approveSkill_personalMode_returns403() {
        stubPersonalRequest("owner@test.com");

        ResponseEntity<?> resp = controller.approveSkill("skill-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(skillService);
    }

    @Test
    void approveSkill_memberRole_returns403() {
        stubTeamRequest("member@test.com");
        doThrow(new SecurityException("Only the organization owner can perform this action."))
                .when(orgService).requireOwner("skyproton", "member@test.com");

        ResponseEntity<?> resp = controller.approveSkill("skill-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(skillService);
    }

    @Test
    void approveSkill_notFound_returns400() {
        stubTeamRequest("owner@test.com");
        doNothing().when(orgService).requireOwner("skyproton", "owner@test.com");
        when(skillService.approve("ghost"))
                .thenThrow(new IllegalArgumentException("Skill not found: ghost"));

        ResponseEntity<?> resp = controller.approveSkill("ghost", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ── rejectSkill ───────────────────────────────────────────────────────────

    @Test
    void rejectSkill_owner_callsServiceAndReturns200() {
        stubTeamRequest("owner@test.com");
        doNothing().when(orgService).requireOwner("skyproton", "owner@test.com");
        doNothing().when(skillService).reject("skill-1");

        ResponseEntity<?> resp = controller.rejectSkill("skill-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(skillService).reject("skill-1");
    }

    @Test
    void rejectSkill_memberRole_returns403() {
        stubTeamRequest("member@test.com");
        doThrow(new SecurityException("Only the organization owner can perform this action."))
                .when(orgService).requireOwner("skyproton", "member@test.com");

        ResponseEntity<?> resp = controller.rejectSkill("skill-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(skillService);
    }

    @Test
    void rejectSkill_notFound_returns400() {
        stubTeamRequest("owner@test.com");
        doNothing().when(orgService).requireOwner("skyproton", "owner@test.com");
        doThrow(new IllegalArgumentException("Skill not found: ghost"))
                .when(skillService).reject("ghost");

        ResponseEntity<?> resp = controller.rejectSkill("ghost", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ── existing member management endpoints (regression) ─────────────────────

    @Test
    void listMembers_teamMode_delegatesToService() {
        stubTeamRequest("owner@test.com");
        OrgMember m = new OrgMember("skyproton", "owner@test.com", OrgMember.Role.OWNER);
        when(orgService.listMembers("skyproton")).thenReturn(List.of(m));

        ResponseEntity<?> resp = controller.listMembers(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void listMembers_personalMode_returns403() {
        stubPersonalRequest("owner@test.com");

        ResponseEntity<?> resp = controller.listMembers(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }
}
