package com.agentsystem.skill.service;

import com.agentsystem.skill.service.impl.SkillServiceImpl;

import com.agentsystem.org.OrgContext;
import com.agentsystem.skill.entity.Skill;
import com.agentsystem.skill.entity.SkillVersion;
import com.agentsystem.skill.repository.SkillRepository;
import com.agentsystem.skill.repository.SkillVersionRepository;
import com.agentsystem.storage.StorageClient;
import com.agentsystem.user.entity.User;
import com.agentsystem.user.entity.UserStatus;
import com.agentsystem.user.service.UserAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    @Mock SkillRepository repo;
    @Mock SkillVersionRepository versionRepo;
    @Mock StorageClient storageClient;
    @Mock SkillTextExtractor textExtractor;
    @Mock UserAccountService userAccountService;

    @InjectMocks SkillServiceImpl service;

    @BeforeEach
    void setUp() {
        // Resolve every email used across this file to a uuid equal to itself, for simplicity.
        for (String email : List.of("owner@test.com", "other@test.com", "member@test.com")) {
            lenient().when(userAccountService.findByEmail(email))
                    .thenReturn(Optional.of(new User(email, email, UserStatus.USER, true)));
            lenient().when(userAccountService.getEmailByUuid(email)).thenReturn(email);
        }
    }

    private static SkillVersion version(String id, String skillId, int num, String status) {
        return new SkillVersion(id, skillId, num, "obj-" + id, "f.py", "python", 10L, status,
                "owner@test.com", Instant.now());
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_personalMode_returnsOwnedSkills() {
        Skill skill = new Skill("id-1", "owner@test.com", "My Script");
        when(repo.findByOwnerUuidAndOrgIdIsNullOrderByCreatedAtDesc("owner@test.com"))
                .thenReturn(List.of(skill));
        when(versionRepo.findTopBySkillIdOrderByVersionNumberDesc("id-1"))
                .thenReturn(Optional.of(version("v-1", "id-1", 1, "APPROVED")));

        List<SkillService.SkillSummary> result = service.list(new OrgContext("owner@test.com", "owner@test.com", "PERSONAL", null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("id-1");
        assertThat(result.get(0).versionNumber()).isEqualTo(1);
        assertThat(result.get(0).status()).isEqualTo("APPROVED");
    }

    @Test
    void list_nullCtx_returnsAllSkills() {
        Skill s1 = new Skill("id-1", "a@test.com", "A");
        Skill s2 = new Skill("id-2", "b@test.com", "B");
        when(repo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(s1, s2));

        List<SkillService.SkillSummary> result = service.list(null);

        assertThat(result).extracting(SkillService.SkillSummary::id).containsExactly("id-1", "id-2");
        verify(repo, never()).findByOwnerUuidAndOrgIdIsNullOrderByCreatedAtDesc(any());
    }

    @Test
    void list_teamMode_returnsOrgSkillsViaMemberQuery() {
        Skill skill = new Skill("id-1", "creator@test.com", "Shared Tool");
        skill.setOrgId("skyproton");
        when(repo.findByOrgIdForMember("skyproton", "member@test.com")).thenReturn(List.of(skill));

        OrgContext teamCtx = new OrgContext("member@test.com", "member@test.com", "TEAM", "skyproton");
        List<SkillService.SkillSummary> result = service.list(teamCtx);

        assertThat(result).extracting(SkillService.SkillSummary::id).containsExactly("id-1");
        verify(repo).findByOrgIdForMember("skyproton", "member@test.com");
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_savesSkillAndVersion1WithAllFields() throws Exception {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(storageClient.uploadObject(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new StorageClient.UploadResult("obj-1", "skill/obj-1"));
        ArgumentCaptor<SkillVersion> captor = ArgumentCaptor.forClass(SkillVersion.class);

        var file = new MockMultipartFile("file", "tool.py", "text/x-python", "import os".getBytes());
        Skill result = service.create("owner@test.com", "My Tool", file, "python");

        verify(versionRepo).save(captor.capture());
        assertThat(result.getOwnerUuid()).isEqualTo("owner@test.com");
        assertThat(result.getName()).isEqualTo("My Tool");
        assertThat(captor.getValue().getSkillId()).isEqualTo(result.getId());
        assertThat(captor.getValue().getVersionNumber()).isEqualTo(1);
        assertThat(captor.getValue().getFileName()).isEqualTo("tool.py");
        assertThat(captor.getValue().getFileType()).isEqualTo("python");
        assertThat(captor.getValue().getObjectId()).isEqualTo("obj-1");
        assertThat(captor.getValue().getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void create_teamMode_setsOrgIdAndPendingVersion() throws Exception {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(storageClient.uploadObject(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new StorageClient.UploadResult("obj-1", "skill/obj-1"));
        ArgumentCaptor<SkillVersion> captor = ArgumentCaptor.forClass(SkillVersion.class);

        OrgContext teamCtx = new OrgContext("owner@test.com", "owner@test.com", "TEAM", "skyproton");
        var file = new MockMultipartFile("file", "t.py", "text/x-python", "code".getBytes());
        Skill result = service.create(teamCtx, "Team Tool", file, "python");

        verify(versionRepo).save(captor.capture());
        assertThat(result.getOrgId()).isEqualTo("skyproton");
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void create_pdfFileType_extractsTextViaTikaBeforeUpload() throws Exception {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(textExtractor.extract(any(), anyString())).thenReturn("extracted pdf text");
        when(storageClient.uploadObject(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new StorageClient.UploadResult("obj-1", "skill/obj-1"));
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);

        var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});
        service.create("owner@test.com", "PDF Skill", file, "pdf");

        verify(textExtractor).extract(any(), eq("doc.pdf"));
        verify(storageClient).uploadObject(bytesCaptor.capture(), eq("doc.pdf"), eq("text/plain"),
                eq("owner@test.com"), anyString(), anyString());
        assertThat(new String(bytesCaptor.getValue())).isEqualTo("extracted pdf text");
    }

    @Test
    void create_csvFileType_skipsExtraction() throws Exception {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(storageClient.uploadObject(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new StorageClient.UploadResult("obj-1", "skill/obj-1"));

        var file = new MockMultipartFile("file", "data.csv", "text/csv", "a,b,c".getBytes());
        service.create("owner@test.com", "CSV Skill", file, "csv");

        verifyNoInteractions(textExtractor);
    }

    // ── addVersion ────────────────────────────────────────────────────────────

    @Test
    void addVersion_incrementsVersionNumber() throws Exception {
        Skill skill = new Skill("id-1", "owner@test.com", "S");
        when(repo.findById("id-1")).thenReturn(Optional.of(skill));
        when(versionRepo.findMaxVersionNumber("id-1")).thenReturn(2);
        when(storageClient.uploadObject(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new StorageClient.UploadResult("obj-2", "skill/obj-2"));
        ArgumentCaptor<SkillVersion> captor = ArgumentCaptor.forClass(SkillVersion.class);

        var file = new MockMultipartFile("file", "v3.py", "text/x-python", "code".getBytes());
        service.addVersion(new OrgContext("owner@test.com", "owner@test.com", "PERSONAL", null), "id-1", file, "python");

        verify(versionRepo).save(captor.capture());
        assertThat(captor.getValue().getVersionNumber()).isEqualTo(3);
        assertThat(captor.getValue().getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void addVersion_teamMode_requiresReapproval() throws Exception {
        Skill skill = new Skill("id-1", "owner@test.com", "S");
        skill.setOrgId("skyproton");
        when(repo.findById("id-1")).thenReturn(Optional.of(skill));
        when(versionRepo.findMaxVersionNumber("id-1")).thenReturn(1);
        when(storageClient.uploadObject(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new StorageClient.UploadResult("obj-2", "skill/obj-2"));
        ArgumentCaptor<SkillVersion> captor = ArgumentCaptor.forClass(SkillVersion.class);

        var file = new MockMultipartFile("file", "v2.py", "text/x-python", "code".getBytes());
        service.addVersion(new OrgContext("member@test.com", "member@test.com", "TEAM", "skyproton"), "id-1", file, "python");

        verify(versionRepo).save(captor.capture());
        assertThat(captor.getValue().getVersionNumber()).isEqualTo(2);
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void addVersion_nonOwnerPersonalMode_throwsSecurityException() {
        Skill skill = new Skill("id-1", "owner@test.com", "S");
        when(repo.findById("id-1")).thenReturn(Optional.of(skill));

        var file = new MockMultipartFile("file", "v2.py", "text/x-python", "code".getBytes());
        assertThatThrownBy(() ->
                service.addVersion(new OrgContext("other@test.com", "other@test.com", "PERSONAL", null), "id-1", file, "python"))
                .isInstanceOf(SecurityException.class);
        verify(versionRepo, never()).save(any());
    }

    @Test
    void addVersion_skillNotFound_throwsIllegalArgument() {
        when(repo.findById("ghost")).thenReturn(Optional.empty());
        var file = new MockMultipartFile("file", "v2.py", "text/x-python", "code".getBytes());

        assertThatThrownBy(() ->
                service.addVersion(new OrgContext("owner@test.com", "owner@test.com", "PERSONAL", null), "ghost", file, "python"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── getContent / getVersionContent ───────────────────────────────────────

    @Test
    void getContent_returnsActiveApprovedVersionContent() {
        when(versionRepo.findTopBySkillIdAndStatusOrderByVersionNumberDesc("id-1", "APPROVED"))
                .thenReturn(Optional.of(version("v-1", "id-1", 2, "APPROVED")));
        when(storageClient.downloadObject("obj-v-1")).thenReturn("print('hello')".getBytes());

        assertThat(service.getContent("id-1")).contains("print('hello')");
    }

    @Test
    void getContent_noApprovedVersion_returnsEmpty() {
        when(versionRepo.findTopBySkillIdAndStatusOrderByVersionNumberDesc("id-1", "APPROVED"))
                .thenReturn(Optional.empty());

        assertThat(service.getContent("id-1")).isEmpty();
    }

    @Test
    void getVersionContent_existing_returnsContent() {
        when(versionRepo.findBySkillIdAndVersionNumber("id-1", 1))
                .thenReturn(Optional.of(version("v-1", "id-1", 1, "PENDING")));
        when(storageClient.downloadObject("obj-v-1")).thenReturn("old code".getBytes());

        assertThat(service.getVersionContent("id-1", 1)).contains("old code");
    }

    @Test
    void listVersions_returnsAllVersionsDescending() {
        List<SkillVersion> versions = List.of(version("v-2", "id-1", 2, "PENDING"), version("v-1", "id-1", 1, "APPROVED"));
        when(versionRepo.findBySkillIdOrderByVersionNumberDesc("id-1")).thenReturn(versions);

        assertThat(service.listVersions("id-1")).containsExactlyElementsOf(versions);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_ownerCanDelete_deletesObjectsAndSkill() {
        Skill skill = new Skill("id-1", "owner@test.com", "S");
        when(repo.findById("id-1")).thenReturn(Optional.of(skill));
        when(versionRepo.findBySkillIdOrderByVersionNumberDesc("id-1"))
                .thenReturn(List.of(version("v-1", "id-1", 1, "APPROVED")));

        service.delete("id-1", "owner@test.com");

        verify(storageClient).deleteObject("obj-v-1", "owner@test.com");
        verify(repo).deleteById("id-1");
    }

    @Test
    void delete_nonOwnerThrowsSecurityException() {
        Skill skill = new Skill("id-1", "owner@test.com", "S");
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
    void delete_ownerUuidMatches_deletesSuccessfully() {
        // Uuid comparison is exact (no case-folding needed — normalisation happens once,
        // at email->uuid resolution time, not at every comparison).
        Skill skill = new Skill("id-1", "owner@test.com", "S");
        when(repo.findById("id-1")).thenReturn(Optional.of(skill));
        when(versionRepo.findBySkillIdOrderByVersionNumberDesc("id-1")).thenReturn(List.of());

        service.delete("id-1", "owner@test.com");

        verify(repo).deleteById("id-1");
    }

    @Test
    void delete_teamMode_anyMemberCanDelete() {
        Skill skill = new Skill("id-1", "creator@test.com", "S");
        skill.setOrgId("skyproton");
        when(repo.findById("id-1")).thenReturn(Optional.of(skill));
        when(versionRepo.findBySkillIdOrderByVersionNumberDesc("id-1")).thenReturn(List.of());

        OrgContext teamCtx = new OrgContext("member@test.com", "member@test.com", "TEAM", "skyproton");
        service.delete("id-1", teamCtx);

        verify(repo).deleteById("id-1");
    }

    // ── listPendingByOrg ──────────────────────────────────────────────────────

    @Test
    void listPendingByOrg_returnsPendingVersionsEnrichedWithSkillInfo() {
        Skill skill = new Skill("id-1", "member@test.com", "Draft Tool");
        skill.setOrgId("skyproton");
        when(versionRepo.findPendingByOrgId("skyproton"))
                .thenReturn(List.of(version("v-1", "id-1", 2, "PENDING")));
        when(repo.findById("id-1")).thenReturn(Optional.of(skill));

        List<SkillService.PendingSkillVersion> result = service.listPendingByOrg("skyproton");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).skillName()).isEqualTo("Draft Tool");
        assertThat(result.get(0).ownerUuid()).isEqualTo("member@test.com");
        assertThat(result.get(0).versionNumber()).isEqualTo(2);
    }

    @Test
    void listPendingByOrg_emptyWhenNoPending() {
        when(versionRepo.findPendingByOrgId("skyproton")).thenReturn(List.of());

        assertThat(service.listPendingByOrg("skyproton")).isEmpty();
    }

    // ── approve ───────────────────────────────────────────────────────────────

    @Test
    void approve_existingId_setsApproved() {
        SkillVersion v = version("v-1", "id-1", 2, "PENDING");
        when(versionRepo.findById("v-1")).thenReturn(Optional.of(v));
        when(versionRepo.save(v)).thenReturn(v);

        SkillVersion result = service.approve("v-1");

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        verify(versionRepo).save(v);
    }

    @Test
    void approve_notFound_throwsIllegalArgument() {
        when(versionRepo.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ── reject ────────────────────────────────────────────────────────────────

    @Test
    void reject_existingId_setsRejected() {
        SkillVersion v = version("v-1", "id-1", 2, "PENDING");
        when(versionRepo.findById("v-1")).thenReturn(Optional.of(v));
        when(versionRepo.save(v)).thenReturn(v);

        service.reject("v-1");

        assertThat(v.getStatus()).isEqualTo("REJECTED");
        verify(versionRepo).save(v);
    }

    @Test
    void reject_notFound_throwsIllegalArgument() {
        when(versionRepo.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reject("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
