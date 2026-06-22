package com.agentsystem.skill.service;

import com.agentsystem.org.OrgContext;
import com.agentsystem.skill.entity.Skill;
import com.agentsystem.skill.entity.SkillVersion;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SkillService {

    /** Metadata-only summary for list views — sourced from the skill's latest version. */
    record SkillSummary(
            String id, String ownerEmail, String orgId, String name,
            String fileName, String fileType, long size, String status,
            int versionNumber, Instant createdAt) {}

    /** A pending version enriched with its parent skill's name/owner for the approval queue UI. */
    record PendingSkillVersion(
            String versionId, String skillId, String skillName, String ownerEmail,
            int versionNumber, String fileName, String fileType, long sizeBytes,
            String createdByEmail, Instant createdAt) {}

    /**
     * List skills for the UI.
     * Team mode: skills with an approved version + caller's own submissions.
     * Personal: owned skills only.
     */
    List<SkillSummary> list(OrgContext ctx);

    Skill create(OrgContext ctx, String name, MultipartFile file, String fileType) throws IOException;

    Skill create(String ownerEmail, String name, MultipartFile file, String fileType) throws IOException;

    /** Upload a new version of an existing skill. Team mode: goes back to PENDING for re-approval. */
    SkillVersion addVersion(OrgContext ctx, String skillId, MultipartFile file, String fileType) throws IOException;

    List<SkillVersion> listVersions(String skillId);

    Optional<String> getVersionContent(String skillId, int versionNumber);

    /** The active version's content — latest APPROVED. Called by WorkflowRunService for prompt injection. */
    Optional<String> getContent(String skillId);

    /** All PENDING versions for the org (owner approval queue). */
    List<PendingSkillVersion> listPendingByOrg(String orgId);

    /** Approve a pending version (owner only — caller must enforce ownership). */
    SkillVersion approve(String versionId);

    /** Reject a pending version: sets REJECTED status. */
    void reject(String versionId);

    void delete(String id, OrgContext ctx);

    void delete(String id, String callerEmail);
}
