package com.agentsystem.org.controller;

import com.agentsystem.knowledge.service.KnowledgeSourceService;
import com.agentsystem.knowledge.entity.KnowledgeSource;
import com.agentsystem.org.entity.OrgMember;
import com.agentsystem.org.service.OrganizationService;
import com.agentsystem.skill.service.SkillService;
import com.agentsystem.skill.entity.SkillVersion;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
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

    private SkillVersion pendingSkillVersion(String versionId) {
        return new SkillVersion(versionId, "skill-1", 2, "obj-" + versionId, "d.py", "python", 50L,
                "PENDING", "member@test.com", Instant.now());
    }

    private SkillService.PendingSkillVersion pendingSkillSummary(String versionId) {
        return new SkillService.PendingSkillVersion(
                versionId, "skill-1", "Draft Tool", "member@test.com", 2, "d.py", "python", 50L,
                "member@test.com", Instant.now());
    }

    // ── listApprovals ─────────────────────────────────────────────────────────

    @Test
    void listApprovals_ownerReturns200WithPendingItems() {
        stubTeamRequest("owner@test.com");
        doNothing().when(orgService).requireOwner("skyproton", "owner@test.com");
        KnowledgeSource kb = pendingKb(1L, "doc.pdf");
        var pendingVersion = pendingSkillSummary("version-1");
        when(knowledgeSourceService.listPendingByOrg("skyproton")).thenReturn(List.of(kb));
        when(skillService.listPendingByOrg("skyproton")).thenReturn(List.of(pendingVersion));

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
        SkillVersion approved = pendingSkillVersion("version-1");
        approved.setStatus("APPROVED");
        when(skillService.approve("version-1")).thenReturn(approved);

        ResponseEntity<?> resp = controller.approveSkill("version-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(skillService).approve("version-1");
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

    // ── addMember ─────────────────────────────────────────────────────────────

    @Test
    void addMember_personalMode_returns403() {
        stubPersonalRequest("owner@test.com");

        ResponseEntity<?> resp = controller.addMember(Map.of("email", "new@test.com"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(orgService);
    }

    @Test
    void addMember_missingEmail_returns400() {
        stubTeamRequest("owner@test.com");

        ResponseEntity<?> resp = controller.addMember(Map.of("role", "MEMBER"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) resp.getBody();
        assertThat(body).containsEntry("error", "email is required");
    }

    @Test
    void addMember_notOwner_returns403() {
        stubTeamRequest("member@test.com");
        doThrow(new SecurityException("Only the organization owner can perform this action."))
                .when(orgService).requireOwner("skyproton", "member@test.com");

        ResponseEntity<?> resp = controller.addMember(Map.of("email", "new@test.com"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void addMember_success_returns201() {
        stubTeamRequest("owner@test.com");
        doNothing().when(orgService).requireOwner("skyproton", "owner@test.com");
        OrgMember member = new OrgMember("skyproton", "new@test.com", OrgMember.Role.MEMBER);
        when(orgService.addMember("skyproton", "new@test.com", OrgMember.Role.MEMBER)).thenReturn(member);

        ResponseEntity<?> resp = controller.addMember(Map.of("email", "new@test.com", "role", "MEMBER"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody()).isEqualTo(member);
    }

    @Test
    void addMember_badRole_returns400() {
        stubTeamRequest("owner@test.com");
        doNothing().when(orgService).requireOwner("skyproton", "owner@test.com");

        // OrgMember.Role.valueOf("SUPERADMIN") will throw IllegalArgumentException
        ResponseEntity<?> resp = controller.addMember(
                Map.of("email", "new@test.com", "role", "SUPERADMIN"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ── removeMember ──────────────────────────────────────────────────────────

    @Test
    void removeMember_personalMode_returns403() {
        stubPersonalRequest("owner@test.com");

        ResponseEntity<?> resp = controller.removeMember("member@test.com", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(orgService);
    }

    @Test
    void removeMember_ownerRemovesSelf_returns400() {
        stubTeamRequest("owner@test.com");
        doNothing().when(orgService).requireOwner("skyproton", "owner@test.com");

        ResponseEntity<?> resp = controller.removeMember("owner@test.com", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) resp.getBody();
        assertThat(body.get("error")).contains("cannot remove themselves");
    }

    @Test
    void removeMember_notOwner_returns403() {
        stubTeamRequest("member@test.com");
        doThrow(new SecurityException("Only the organization owner can perform this action."))
                .when(orgService).requireOwner("skyproton", "member@test.com");

        ResponseEntity<?> resp = controller.removeMember("other@test.com", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void removeMember_success_returns204() {
        stubTeamRequest("owner@test.com");
        doNothing().when(orgService).requireOwner("skyproton", "owner@test.com");
        doNothing().when(orgService).removeMember("skyproton", "member@test.com");

        ResponseEntity<?> resp = controller.removeMember("member@test.com", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
        verify(orgService).removeMember("skyproton", "member@test.com");
    }

    // ── transferOwner ─────────────────────────────────────────────────────────

    @Test
    void transferOwner_personalMode_returns403() {
        stubPersonalRequest("owner@test.com");

        ResponseEntity<?> resp = controller.transferOwner(Map.of("email", "new@test.com"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(orgService);
    }

    @Test
    void transferOwner_missingEmail_returns400() {
        stubTeamRequest("owner@test.com");

        ResponseEntity<?> resp = controller.transferOwner(Map.of("role", "OWNER"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) resp.getBody();
        assertThat(body).containsEntry("error", "email is required");
    }

    @Test
    void transferOwner_notOwner_returns403() {
        stubTeamRequest("member@test.com");
        doThrow(new SecurityException("Only the organization owner can perform this action."))
                .when(orgService).transferOwner("skyproton", "member@test.com", "new@test.com");

        ResponseEntity<?> resp = controller.transferOwner(Map.of("email", "new@test.com"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void transferOwner_success_returns200() {
        stubTeamRequest("owner@test.com");
        doNothing().when(orgService).transferOwner("skyproton", "owner@test.com", "new@test.com");

        ResponseEntity<?> resp = controller.transferOwner(Map.of("email", "new@test.com"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body.get("message").toString()).contains("new@test.com");
        verify(orgService).transferOwner("skyproton", "owner@test.com", "new@test.com");
    }
}
