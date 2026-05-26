package com.ragagent.skill;

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
    void list_withOwnerEmail_returnsOwnerSkills() {
        Skill skill = new Skill("id-1", "owner@test.com", "My Script", "script.py", "python", 100, "print('hi')");
        when(repo.findByOwnerEmailOrderByCreatedAtDesc("owner@test.com")).thenReturn(List.of(skill));

        List<Skill> result = service.list("owner@test.com");

        assertThat(result).containsExactly(skill);
    }

    @Test
    void list_nullOwnerEmail_returnsAllSkills() {
        Skill s1 = new Skill("id-1", "a@test.com", "A", "a.py", "python", 10, "");
        Skill s2 = new Skill("id-2", "b@test.com", "B", "b.py", "python", 20, "");
        when(repo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(s1, s2));

        List<Skill> result = service.list(null);

        assertThat(result).containsExactly(s1, s2);
        verify(repo, never()).findByOwnerEmailOrderByCreatedAtDesc(any());
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
}
