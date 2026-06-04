package com.ragagent.skill;

import com.ragagent.org.OrgContext;
import com.ragagent.skill.entity.Skill;
import com.ragagent.skill.repository.SkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    @Mock SkillRepository repo;

    @InjectMocks SkillService service;

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_withOwnerEmail_returnsPersonalSkills() {
        Skill skill = new Skill("id-1", "owner@test.com", "My Script", "script.py", "python", 100, "print('hi')");
        when(repo.findByOwnerEmailAndOrgIdIsNullOrderByCreatedAtDesc("owner@test.com"))
                .thenReturn(List.of(skill));

        List<Skill> result = service.list("owner@test.com");

        assertThat(result).containsExactly(skill);
    }

    @Test
    void list_nullOwnerEmail_returnsAllSkills() {
        Skill s1 = new Skill("id-1", "a@test.com", "A", "a.py", "python", 10, "");
        Skill s2 = new Skill("id-2", "b@test.com", "B", "b.py", "python", 20, "");
        when(repo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(s1, s2));

        List<Skill> result = service.list((String) null);

        assertThat(result).containsExactly(s1, s2);
        verify(repo, never()).findByOwnerEmailAndOrgIdIsNullOrderByCreatedAtDesc(any());
    }

    @Test
    void list_teamMode_returnsOrgSkillsViaMemberQuery() {
        Skill skill = new Skill("id-1", "creator@test.com", "Shared Tool", "t.py", "python", 50, "");
        skill.setOrgId("skyproton");
        when(repo.findByOrgIdForMember("skyproton", "member@test.com")).thenReturn(List.of(skill));

        OrgContext teamCtx = new OrgContext("member@test.com", "TEAM", "skyproton");
        List<Skill> result = service.list(teamCtx);

        assertThat(result).containsExactly(skill);
        verify(repo).findByOrgIdForMember("skyproton", "member@test.com");
    }

    @Test
    void create_teamMode_setsOrgIdAndPendingStatus() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        OrgContext teamCtx = new OrgContext("owner@test.com", "TEAM", "skyproton");
        Skill result = service.create(teamCtx, "Team Tool", "t.py", "python", 100, "code");

        assertThat(result.getOrgId()).isEqualTo("skyproton");
        assertThat(result.getOwnerEmail()).isEqualTo("owner@test.com");
        assertThat(result.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void delete_teamMode_anyMemberCanDelete() {
        Skill skill = new Skill("id-1", "creator@test.com", "S", "s.py", "python", 10, "");
        skill.setOrgId("skyproton");
        when(repo.findById("id-1")).thenReturn(Optional.of(skill));

        OrgContext teamCtx = new OrgContext("member@test.com", "TEAM", "skyproton");
        service.delete("id-1", teamCtx);

        verify(repo).deleteById("id-1");
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_savesSkillWithAllFields() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);

        Skill result = service.create("owner@test.com", "My Tool", "tool.py", "python", 200, "import os");

        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getOwnerEmail()).isEqualTo("owner@test.com");
        assertThat(captor.getValue().getName()).isEqualTo("My Tool");
        assertThat(captor.getValue().getFileName()).isEqualTo("tool.py");
        assertThat(captor.getValue().getFileType()).isEqualTo("python");
        assertThat(captor.getValue().getSize()).isEqualTo(200L);
        assertThat(captor.getValue().getContent()).isEqualTo("import os");
        assertThat(captor.getValue().getId()).isNotBlank();
    }

    @Test
    void create_returnsSkill() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        Skill result = service.create("owner@test.com", "Tool", "t.js", "javascript", 50, "console.log('hi')");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Tool");
    }

    // ── getContent ────────────────────────────────────────────────────────────

    @Test
    void getContent_existing_returnsContent() {
        Skill skill = new Skill("id-1", "owner@test.com", "S", "s.py", "python", 10, "print('hello')");
        when(repo.findById("id-1")).thenReturn(Optional.of(skill));

        assertThat(service.getContent("id-1")).contains("print('hello')");
    }

    @Test
    void getContent_notFound_returnsEmpty() {
        when(repo.findById("ghost")).thenReturn(Optional.empty());

        assertThat(service.getContent("ghost")).isEmpty();
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_ownerCanDelete() {
        Skill skill = new Skill("id-1", "owner@test.com", "S", "s.py", "python", 10, "");
        when(repo.findById("id-1")).thenReturn(Optional.of(skill));

        service.delete("id-1", "owner@test.com");

        verify(repo).deleteById("id-1");
    }

    @Test
    void delete_nonOwnerThrowsSecurityException() {
        Skill skill = new Skill("id-1", "owner@test.com", "S", "s.py", "python", 10, "");
        when(repo.findById("id-1")).thenReturn(Optional.of(skill));

        assertThatThrownBy(() -> service.delete("id-1", "other@test.com"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Only the owner");

        verify(repo, never()).deleteById(any());
    }

    @Test
    void delete_notFound_noOp() {
        when(repo.findById("ghost")).thenReturn(Optional.empty());

        service.delete("ghost", "owner@test.com");

        verify(repo, never()).deleteById(any());
    }

    @Test
    void delete_caseInsensitiveEmailMatch() {
        Skill skill = new Skill("id-1", "Owner@Test.COM", "S", "s.py", "python", 10, "");
        when(repo.findById("id-1")).thenReturn(Optional.of(skill));

        service.delete("id-1", "owner@test.com");

        verify(repo).deleteById("id-1");
    }

    // ── create: personal mode stays APPROVED ──────────────────────────────────

    @Test
    void create_personalMode_statusRemainsApproved() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        Skill result = service.create("owner@test.com", "My Tool", "t.py", "python", 100, "code");

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(result.getOrgId()).isNull();
    }

    // ── listPendingByOrg ──────────────────────────────────────────────────────

    @Test
    void listPendingByOrg_returnsPendingSkillsForOrg() {
        Skill pending = new Skill("id-p", "member@test.com", "Draft Tool", "d.py", "python", 50, "code");
        pending.setOrgId("skyproton");
        pending.setStatus("PENDING");
        when(repo.findPendingByOrgId("skyproton")).thenReturn(List.of(pending));

        List<Skill> result = service.listPendingByOrg("skyproton");

        assertThat(result).containsExactly(pending);
        verify(repo).findPendingByOrgId("skyproton");
    }

    @Test
    void listPendingByOrg_emptyWhenNoPending() {
        when(repo.findPendingByOrgId("skyproton")).thenReturn(List.of());

        assertThat(service.listPendingByOrg("skyproton")).isEmpty();
    }

    // ── approve ───────────────────────────────────────────────────────────────

    @Test
    void approve_existingId_setsApproved() {
        Skill skill = new Skill("id-1", "member@test.com", "Draft", "d.py", "python", 50, "code");
        skill.setStatus("PENDING");
        when(repo.findById("id-1")).thenReturn(Optional.of(skill));
        when(repo.save(skill)).thenReturn(skill);

        Skill result = service.approve("id-1");

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        verify(repo).save(skill);
    }

    @Test
    void approve_notFound_throwsIllegalArgument() {
        when(repo.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ── reject ────────────────────────────────────────────────────────────────

    @Test
    void reject_existingId_setsRejected() {
        Skill skill = new Skill("id-1", "member@test.com", "Draft", "d.py", "python", 50, "code");
        skill.setStatus("PENDING");
        when(repo.findById("id-1")).thenReturn(Optional.of(skill));
        when(repo.save(skill)).thenReturn(skill);

        service.reject("id-1");

        assertThat(skill.getStatus()).isEqualTo("REJECTED");
        verify(repo).save(skill);
    }

    @Test
    void reject_notFound_throwsIllegalArgument() {
        when(repo.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reject("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
