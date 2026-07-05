package com.agentsystem.knowledge.service.impl;

import com.agentsystem.knowledge.service.KnowledgeSourceService;

import com.agentsystem.knowledge.entity.KnowledgeSource;
import com.agentsystem.knowledge.entity.KnowledgeSourceShare;
import com.agentsystem.knowledge.repository.KnowledgeSourceRepository;
import com.agentsystem.org.OrgContext;
import com.agentsystem.rag.service.DocumentIngestionService;
import com.agentsystem.user.entity.User;
import com.agentsystem.user.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSourceServiceImpl implements KnowledgeSourceService {

    private final KnowledgeSourceRepository repo;
    private final DocumentIngestionService  ingestionService;
    private final UserAccountService        userAccountService;

    /**
     * Record (or update) a source after successful ingestion.
     * In team mode, new uploads are set to PENDING until an owner approves.
     */
    @Transactional
    @Override
    public void upsert(String source, String label, String category, int chunkCount,
                       String ownerEmail, String orgId) {
        upsertByUuid(source, label, category, chunkCount, resolveUuid(ownerEmail), orgId);
    }

    /** Backward-compatible overload for personal mode callers. */
    @Transactional
    @Override
    public void upsert(String source, String label, String category, int chunkCount, String ownerEmail) {
        upsert(source, label, category, chunkCount, ownerEmail, null);
    }

    @Transactional
    @Override
    public void upsert(String source, String label, String category, int chunkCount, OrgContext ctx) {
        upsertByUuid(source, label, category, chunkCount,
                ctx != null ? ctx.userUuid() : null, ctx != null ? ctx.orgId() : null);
    }

    private void upsertByUuid(String source, String label, String category, int chunkCount,
                               String ownerUuid, String orgId) {
        var existing = orgId != null
                ? repo.findBySourceAndOrgId(source, orgId)
                : repo.findBySource(source);
        existing.ifPresentOrElse(
                ks -> {
                    ks.setLabel(label);
                    ks.setCategory(category);
                    ks.setChunkCount(chunkCount);
                    if (ks.getOwnerUuid() == null && ownerUuid != null) ks.setOwnerUuid(ownerUuid);
                    repo.save(ks);
                },
                () -> {
                    KnowledgeSource ks = new KnowledgeSource(source, label, category, chunkCount, ownerUuid, orgId);
                    if (orgId != null) ks.setStatus("PENDING");
                    repo.save(ks);
                }
        );
    }

    /**
     * List sources visible given the org context (for management UI).
     * Team mode: APPROVED sources + caller's own PENDING/REJECTED submissions.
     * Personal: owned or shared with the caller.
     */
    @Transactional(readOnly = true)
    @Override
    public List<KnowledgeSource> listAccessible(OrgContext ctx) {
        if (ctx == null || ctx.userUuid() == null) return repo.findAllByOrderByIngestedAtDesc();
        if (ctx.isTeam()) return repo.findByOrgIdForMember(ctx.orgId(), ctx.userUuid());
        return repo.findAccessibleByUuid(ctx.userUuid());
    }

    @Transactional(readOnly = true)
    @Override
    public List<KnowledgeSource> listAccessible(String email) {
        String uuid = resolveUuid(email);
        if (uuid == null) return repo.findAllByOrderByIngestedAtDesc();
        return repo.findAccessibleByUuid(uuid);
    }

    /** All PENDING sources for the org (owner approval queue). */
    @Transactional(readOnly = true)
    @Override
    public List<KnowledgeSource> listPendingByOrg(String orgId) {
        return repo.findPendingByOrgId(orgId);
    }

    /** Approve a pending knowledge source (owner only — caller must enforce ownership). */
    @Transactional
    @Override
    public KnowledgeSource approve(Long id) {
        KnowledgeSource ks = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge source not found: " + id));
        ks.setStatus("APPROVED");
        log.info("[KnowledgeSourceService] Approved KB source id={} source='{}'", id, ks.getSource());
        return repo.save(ks);
    }

    /** Reject a pending knowledge source: sets REJECTED and removes from Weaviate. */
    @Transactional
    @Override
    public void reject(Long id) {
        KnowledgeSource ks = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge source not found: " + id));
        ks.setStatus("REJECTED");
        repo.save(ks);
        ingestionService.deleteBySource(ks.getSource());
        log.info("[KnowledgeSourceService] Rejected KB source id={} source='{}' — removed from Weaviate",
                id, ks.getSource());
    }

    /**
     * Delete a source. Team mode: any org member may delete.
     * Personal mode: only the owner.
     */
    @Transactional
    @Override
    public void delete(String source, OrgContext ctx) {
        String callerUuid = ctx != null ? ctx.userUuid() : null;
        String orgId       = ctx != null && ctx.isTeam() ? ctx.orgId() : null;
        var optional = orgId != null ? repo.findBySourceAndOrgId(source, orgId) : repo.findBySource(source);
        optional.ifPresent(ks -> {
            if (!ctx.isTeam() && callerUuid != null && ks.getOwnerUuid() != null
                    && !ks.getOwnerUuid().equals(callerUuid)) {
                throw new SecurityException("Only the owner can delete this source.");
            }
            ingestionService.deleteBySource(source);
            repo.delete(ks);
            log.info("[KnowledgeSourceService] Deleted source='{}' by {}", source, callerUuid);
        });
    }

    @Transactional
    @Override
    public void delete(String source, String callerEmail) {
        delete(source, new OrgContext(resolveUuid(callerEmail), callerEmail, "PERSONAL", null));
    }

    /**
     * Update label and/or category. Team mode: any org member may edit.
     * Personal mode: only the owner.
     */
    @Transactional
    @Override
    public KnowledgeSource updateMetadata(String source, String label, String category, OrgContext ctx) {
        KnowledgeSource ks = repo.findBySource(source)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + source));
        String callerUuid = ctx != null ? ctx.userUuid() : null;
        if (!ctx.isTeam() && callerUuid != null && ks.getOwnerUuid() != null
                && !ks.getOwnerUuid().equals(callerUuid)) {
            throw new SecurityException("Only the owner can edit this source.");
        }
        if (label    != null) ks.setLabel(label.isBlank()       ? null : label.trim());
        if (category != null) ks.setCategory(category.isBlank() ? null : category.trim());
        return repo.save(ks);
    }

    @Transactional
    @Override
    public KnowledgeSource updateMetadata(String source, String label, String category, String callerEmail) {
        return updateMetadata(source, label, category, new OrgContext(resolveUuid(callerEmail), callerEmail, "PERSONAL", null));
    }

    /**
     * Replace the share-target list. Not applicable in team mode (org-level sharing).
     */
    @Transactional
    @Override
    public KnowledgeSource updateSharing(String source, List<String> emails, String callerEmail) {
        return updateSharing(source, emails, new OrgContext(resolveUuid(callerEmail), callerEmail, "PERSONAL", null));
    }

    @Transactional
    @Override
    public KnowledgeSource updateSharing(String source, List<String> emails, OrgContext ctx) {
        KnowledgeSource ks = repo.findBySource(source)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + source));

        String callerUuid = ctx != null ? ctx.userUuid() : null;
        if (callerUuid != null && ks.getOwnerUuid() != null
                && !ks.getOwnerUuid().equals(callerUuid)) {
            throw new SecurityException("Only the owner can change sharing.");
        }

        ks.getShares().clear();
        // Only entries that already belong to a registered user can be resolved to a uuid —
        // an unregistered email is silently dropped, since it can't be granted sharing access
        // without an account.
        emails.stream()
                .map(String::trim)
                .filter(e -> !e.isBlank())
                .map(this::resolveUuid)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(uuid -> ks.getShares().add(new KnowledgeSourceShare(ks, uuid)));

        return repo.save(ks);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /** Resolves an email to its user_uuid, or null if no such email is a registered user. */
    private String resolveUuid(String email) {
        if (email == null || email.isBlank()) return null;
        return userAccountService.findByEmail(email).map(User::getUuid).orElse(null);
    }
}
