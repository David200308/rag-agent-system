package com.agentsystem.skill;

import com.agentsystem.org.OrgContext;
import com.agentsystem.skill.entity.Skill;
import com.agentsystem.skill.entity.SkillVersion;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
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

    private static MultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "text/plain", content);
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_withEmail_returnsSkills() {
        stubRequest("user@test.com");
        var summary = new SkillService.SkillSummary(
                "id-1", "user@test.com", null, "My Skill", "f.txt", "txt", 5L, "APPROVED", 1, Instant.now());
        when(skillService.list(any(OrgContext.class))).thenReturn(List.of(summary));

        ResponseEntity<List<SkillService.SkillSummary>> resp = controller.list(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    void list_noEmail_returnsEmptyList() {
        when(request.getAttribute("authenticatedEmail")).thenReturn(null);
        when(request.getAttribute("authenticatedMode")).thenReturn(null);
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);
        when(skillService.list(any(OrgContext.class))).thenReturn(List.of());

        ResponseEntity<List<SkillService.SkillSummary>> resp = controller.list(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_validFile_returns201() throws Exception {
        stubRequest("user@test.com");
        when(skillService.create(any(OrgContext.class), anyString(), any(MultipartFile.class), anyString()))
                .thenReturn(new Skill());

        ResponseEntity<?> resp = controller.create(file("file.txt", "Some content".getBytes()), "My Skill", "txt", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void create_emptyFile_returns400() {
        ResponseEntity<?> resp = controller.create(
                new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]), "My Skill", "txt", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void create_fileTypeDefaultsFromExtension() throws Exception {
        stubRequest("user@test.com");
        when(skillService.create(any(OrgContext.class), anyString(), any(MultipartFile.class), eq("md")))
                .thenReturn(new Skill());

        controller.create(file("notes.md", "Hello".getBytes()), "My Skill", null, request);

        verify(skillService).create(any(OrgContext.class), eq("My Skill"), any(MultipartFile.class), eq("md"));
    }

    // ── addVersion ────────────────────────────────────────────────────────────

    @Test
    void addVersion_valid_returns201() throws Exception {
        stubRequest("user@test.com");
        when(skillService.addVersion(any(OrgContext.class), eq("skill-1"), any(MultipartFile.class), anyString()))
                .thenReturn(new SkillVersion());

        ResponseEntity<?> resp = controller.addVersion("skill-1", file("v2.txt", "new content".getBytes()), "txt", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void addVersion_skillNotFound_returns404() throws Exception {
        stubRequest("user@test.com");
        when(skillService.addVersion(any(OrgContext.class), eq("ghost"), any(MultipartFile.class), anyString()))
                .thenThrow(new IllegalArgumentException("Skill not found: ghost"));

        ResponseEntity<?> resp = controller.addVersion("ghost", file("v2.txt", "x".getBytes()), "txt", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void addVersion_notOwner_returns403() throws Exception {
        stubRequest("other@test.com");
        when(skillService.addVersion(any(OrgContext.class), eq("skill-1"), any(MultipartFile.class), anyString()))
                .thenThrow(new SecurityException("not owner"));

        ResponseEntity<?> resp = controller.addVersion("skill-1", file("v2.txt", "x".getBytes()), "txt", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── listVersions / getVersionContent / getContent ────────────────────────

    @Test
    void listVersions_returnsVersionList() {
        when(skillService.listVersions("skill-1")).thenReturn(List.of(new SkillVersion()));

        ResponseEntity<List<SkillVersion>> resp = controller.listVersions("skill-1");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    void getVersionContent_found_returns200() {
        when(skillService.getVersionContent("skill-1", 1)).thenReturn(Optional.of("old content"));

        ResponseEntity<String> resp = controller.getVersionContent("skill-1", 1);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEqualTo("old content");
    }

    @Test
    void getVersionContent_notFound_returns404() {
        when(skillService.getVersionContent("skill-1", 99)).thenReturn(Optional.empty());

        ResponseEntity<String> resp = controller.getVersionContent("skill-1", 99);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

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
