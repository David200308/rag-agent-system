package com.agentsystem.skill;

import com.agentsystem.org.OrgContext;
import com.agentsystem.skill.entity.Skill;
import com.agentsystem.skill.entity.SkillVersion;
import com.agentsystem.skill.repository.SkillRepository;
import com.agentsystem.skill.repository.SkillVersionRepository;
import com.agentsystem.storage.StorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {

    private static final String ENTITY_TYPE = "SKILL";

    /** Binary formats stored as raw bytes get Tika-extracted to plain text before upload. */
    private static final Set<String> EXTRACTED_FILE_TYPES = Set.of("pdf", "docx");

    private final SkillRepository repo;
    private final SkillVersionRepository versionRepo;
    private final StorageClient storageClient;
    private final SkillTextExtractor textExtractor;

    /** Metadata-only summary for list views — sourced from the skill's latest version. */
    public record SkillSummary(
            String id, String ownerEmail, String orgId, String name,
            String fileName, String fileType, long size, String status,
            int versionNumber, Instant createdAt) {}

    /** A pending version enriched with its parent skill's name/owner for the approval queue UI. */
    public record PendingSkillVersion(
            String versionId, String skillId, String skillName, String ownerEmail,
            int versionNumber, String fileName, String fileType, long sizeBytes,
            String createdByEmail, Instant createdAt) {}

    /**
     * List skills for the UI.
     * Team mode: skills with an approved version + caller's own submissions.
     * Personal: owned skills only.
     */
    @Transactional(readOnly = true)
    public List<SkillSummary> list(OrgContext ctx) {
        List<Skill> skills;
        if (ctx == null || ctx.email() == null) {
            skills = repo.findAllByOrderByCreatedAtDesc();
        } else if (ctx.isTeam()) {
            skills = repo.findByOrgIdForMember(ctx.orgId(), ctx.email());
        } else {
            skills = repo.findByOwnerEmailAndOrgIdIsNullOrderByCreatedAtDesc(ctx.email());
        }
        return skills.stream().map(this::toSummary).toList();
    }

    @Transactional
    public Skill create(OrgContext ctx, String name, MultipartFile file, String fileType) throws IOException {
        Skill skill = new Skill(UUID.randomUUID().toString(), ctx.email(), name);
        if (ctx.isTeam()) {
            skill.setOrgId(ctx.orgId());
        }
        repo.save(skill);
        createVersion(ctx, skill, file, fileType, 1);
        log.info("[SkillService] Created skill '{}' (id={}) for {} (org={})",
                name, skill.getId(), ctx.email(), ctx.orgId());
        return skill;
    }

    @Transactional
    public Skill create(String ownerEmail, String name, MultipartFile file, String fileType) throws IOException {
        return create(new OrgContext(ownerEmail, "PERSONAL", null), name, file, fileType);
    }

    /** Upload a new version of an existing skill. Team mode: goes back to PENDING for re-approval. */
    @Transactional
    public SkillVersion addVersion(OrgContext ctx, String skillId, MultipartFile file, String fileType) throws IOException {
        Skill skill = repo.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));
        requireCanModify(skill, ctx);
        int nextVersion = versionRepo.findMaxVersionNumber(skillId) + 1;
        return createVersion(ctx, skill, file, fileType, nextVersion);
    }

    private SkillVersion createVersion(OrgContext ctx, Skill skill, MultipartFile file,
                                        String fileType, int versionNumber) throws IOException {
        byte[] bytes = file.getBytes();
        String contentType = "text/plain";
        if (EXTRACTED_FILE_TYPES.contains(fileType.toLowerCase())) {
            bytes = textExtractor.extract(bytes, file.getOriginalFilename()).getBytes(StandardCharsets.UTF_8);
        }

        StorageClient.UploadResult uploaded = storageClient.uploadObject(
                bytes, file.getOriginalFilename(), contentType,
                ctx.email(), ENTITY_TYPE, skill.getId());

        String status = ctx.isTeam() ? "PENDING" : "APPROVED";
        SkillVersion version = new SkillVersion(
                UUID.randomUUID().toString(), skill.getId(), versionNumber, uploaded.id(),
                file.getOriginalFilename(), fileType, bytes.length, status, ctx.email(), Instant.now());
        versionRepo.save(version);
        log.info("[SkillService] Skill id={} version={} status={} by {}",
                skill.getId(), versionNumber, status, ctx.email());
        return version;
    }

    @Transactional(readOnly = true)
    public List<SkillVersion> listVersions(String skillId) {
        return versionRepo.findBySkillIdOrderByVersionNumberDesc(skillId);
    }

    @Transactional(readOnly = true)
    public Optional<String> getVersionContent(String skillId, int versionNumber) {
        return versionRepo.findBySkillIdAndVersionNumber(skillId, versionNumber)
                .map(v -> new String(storageClient.downloadObject(v.getObjectId()), StandardCharsets.UTF_8));
    }

    /** The active version's content — latest APPROVED. Called by WorkflowRunService for prompt injection. */
    @Transactional(readOnly = true)
    public Optional<String> getContent(String skillId) {
        return versionRepo.findTopBySkillIdAndStatusOrderByVersionNumberDesc(skillId, "APPROVED")
                .map(v -> new String(storageClient.downloadObject(v.getObjectId()), StandardCharsets.UTF_8));
    }

    /** All PENDING versions for the org (owner approval queue). */
    @Transactional(readOnly = true)
    public List<PendingSkillVersion> listPendingByOrg(String orgId) {
        return versionRepo.findPendingByOrgId(orgId).stream()
                .map(v -> {
                    Skill skill = repo.findById(v.getSkillId()).orElse(null);
                    return new PendingSkillVersion(
                            v.getId(), v.getSkillId(),
                            skill != null ? skill.getName() : "(deleted)",
                            skill != null ? skill.getOwnerEmail() : null,
                            v.getVersionNumber(), v.getFileName(), v.getFileType(), v.getSizeBytes(),
                            v.getCreatedByEmail(), v.getCreatedAt());
                })
                .toList();
    }

    /** Approve a pending version (owner only — caller must enforce ownership). */
    @Transactional
    public SkillVersion approve(String versionId) {
        SkillVersion version = versionRepo.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Skill version not found: " + versionId));
        version.setStatus("APPROVED");
        log.info("[SkillService] Approved skill version id={} skillId={} version={}",
                versionId, version.getSkillId(), version.getVersionNumber());
        return versionRepo.save(version);
    }

    /** Reject a pending version: sets REJECTED status. */
    @Transactional
    public void reject(String versionId) {
        SkillVersion version = versionRepo.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Skill version not found: " + versionId));
        version.setStatus("REJECTED");
        versionRepo.save(version);
        log.info("[SkillService] Rejected skill version id={} skillId={} version={}",
                versionId, version.getSkillId(), version.getVersionNumber());
    }

    @Transactional
    public void delete(String id, OrgContext ctx) {
        repo.findById(id).ifPresent(skill -> {
            requireCanModify(skill, ctx);
            for (SkillVersion version : versionRepo.findBySkillIdOrderByVersionNumberDesc(id)) {
                try {
                    storageClient.deleteObject(version.getObjectId(), skill.getOwnerEmail());
                } catch (Exception e) {
                    log.warn("[SkillService] Failed to delete object {} for skill {}: {}",
                            version.getObjectId(), id, e.getMessage());
                }
            }
            repo.deleteById(id); // cascades skill_versions rows via FK
            log.info("[SkillService] Deleted skill id={} by {}", id, ctx.email());
        });
    }

    @Transactional
    public void delete(String id, String callerEmail) {
        delete(id, new OrgContext(callerEmail, "PERSONAL", null));
    }

    /** Team mode: any org member may modify. Personal mode: owner only. */
    private void requireCanModify(Skill skill, OrgContext ctx) {
        if (!ctx.isTeam()) {
            String callerEmail = ctx.email();
            if (callerEmail != null && skill.getOwnerEmail() != null
                    && !skill.getOwnerEmail().equalsIgnoreCase(callerEmail)) {
                throw new SecurityException("Only the owner can modify this skill.");
            }
        }
    }

    private SkillSummary toSummary(Skill skill) {
        SkillVersion latest = versionRepo.findTopBySkillIdOrderByVersionNumberDesc(skill.getId()).orElse(null);
        return new SkillSummary(
                skill.getId(), skill.getOwnerEmail(), skill.getOrgId(), skill.getName(),
                latest != null ? latest.getFileName() : null,
                latest != null ? latest.getFileType() : null,
                latest != null ? latest.getSizeBytes() : 0L,
                latest != null ? latest.getStatus() : "APPROVED",
                latest != null ? latest.getVersionNumber() : 0,
                skill.getCreatedAt());
    }
}
