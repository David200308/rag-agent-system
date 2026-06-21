package com.ragagent.skill;

import com.ragagent.org.OrgContext;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillControllerTest {

    @Mock SkillService        skillService;
    @Mock HttpServletRequest  request;
    @InjectMocks SkillController controller;

    private void stubRequest(String email) {
        when(request.getAttribute("authenticatedEmail")).thenReturn(email);
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_withEmail_returnsSkills() {
        stubRequest("user@test.com");
        Skill skill = new Skill();
        when(skillService.list(any(OrgContext.class))).thenReturn(List.of(skill));

        ResponseEntity<List<Skill>> resp = controller.list(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    void list_noEmail_returnsEmptyList() {
        when(request.getAttribute("authenticatedEmail")).thenReturn(null);
        when(request.getAttribute("authenticatedMode")).thenReturn(null);
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);
        when(skillService.list(any(OrgContext.class))).thenReturn(List.of());

        ResponseEntity<List<Skill>> resp = controller.list(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_validContent_returns201() {
        stubRequest("user@test.com");
        Skill created = new Skill();
        when(skillService.create(any(OrgContext.class), anyString(), anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(created);

        var body = Map.<String, Object>of(
                "name", "My Skill",
                "fileName", "file.txt",
                "fileType", "txt",
                "size", 1024,
                "content", "Some content here"
        );
        ResponseEntity<Skill> resp = controller.create(body, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void create_blankContent_returns400() {
        ResponseEntity<Skill> resp = controller.create(
                Map.of("name", "My Skill", "content", "  "), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void create_missingContent_returns400() {
        ResponseEntity<Skill> resp = controller.create(Map.of("name", "My Skill"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void create_fileNameDefaultsToName() {
        stubRequest("user@test.com");
        when(skillService.create(any(OrgContext.class), eq("My Skill"), eq("My Skill"),
                anyString(), anyLong(), anyString()))
                .thenReturn(new Skill());

        var body = Map.<String, Object>of("name", "My Skill", "content", "Hello");
        controller.create(body, request);

        verify(skillService).create(any(OrgContext.class), eq("My Skill"), eq("My Skill"),
                anyString(), anyLong(), anyString());
    }

    @Test
    void create_fileTypeDefaultsToTxt() {
        stubRequest("user@test.com");
        when(skillService.create(any(OrgContext.class), anyString(), anyString(), eq("txt"), anyLong(), anyString()))
                .thenReturn(new Skill());

        var body = Map.<String, Object>of("name", "My Skill", "content", "Hello");
        controller.create(body, request);

        verify(skillService).create(any(OrgContext.class), anyString(), anyString(), eq("txt"), anyLong(), anyString());
    }

    // ── getContent ────────────────────────────────────────────────────────────

    @Test
    void getContent_found_returns200WithContent() {
        when(skillService.getContent("skill-1")).thenReturn(Optional.of("Skill content here"));

        ResponseEntity<String> resp = controller.getContent("skill-1");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEqualTo("Skill content here");
    }

    @Test
    void getContent_notFound_returns404() {
        when(skillService.getContent("unknown")).thenReturn(Optional.empty());

        ResponseEntity<String> resp = controller.getContent("unknown");

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_success_returns204() {
        stubRequest("user@test.com");
        doNothing().when(skillService).delete(eq("skill-1"), any(OrgContext.class));

        ResponseEntity<Void> resp = controller.delete("skill-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void delete_notOwner_returns403() {
        stubRequest("other@test.com");
        doThrow(new SecurityException("not owner")).when(skillService).delete(anyString(), any(OrgContext.class));

        ResponseEntity<Void> resp = controller.delete("skill-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }
}
