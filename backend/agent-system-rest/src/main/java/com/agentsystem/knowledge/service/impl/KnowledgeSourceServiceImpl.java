package com.agentsystem.knowledge.service.impl;

import com.agentsystem.knowledge.service.KnowledgeSourceService;

import com.agentsystem.knowledge.entity.KnowledgeSource;
import com.agentsystem.knowledge.entity.KnowledgeSourceShare;
import com.agentsystem.knowledge.repository.KnowledgeSourceRepository;
import com.agentsystem.org.OrgContext;
import com.agentsystem.rag.service.DocumentIngestionService;
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

    /**
     * Record (or update) a source after successful ingestion.
     * In team mode, new uploads are set to PENDING until an owner approves.
     */
    @Transactional
    @Override
    public void upsert(String source, String label, String category, int chunkCount,
                       String ownerEmail, String orgId) {
        var existing = orgId != null
                ? repo.findBySourceAndOrgId(source, orgId)
                : repo.findBySource(source);
        existing.ifPresentOrElse(
                ks -> {
                    ks.setLabel(label);
                    ks.setCategory(category);
                    ks.setChunkCount(chunkCount);
                    if (ks.getOwnerEmail() == null && ownerEmail != null) ks.setOwnerEmail(ownerEmail);
                    repo.save(ks);
                },
                () -> {
                    KnowledgeSource ks = new KnowledgeSource(source, label, category, chunkCount, ownerEmail, orgId);
                    if (orgId != null) ks.setStatus("PENDING");
                    repo.save(ks);
                }
        );
    }

    /** Backward-compatible overload for personal mode callers. */
    @Transactional
    @Override
    public void upsert(String source, String label, String category, int chunkCount, String ownerEmail) {
        upsert(source, label, category, chunkCount, ownerEmail, null);
    }

    /**
     * List sources visible given the org context (for management UI).
     * Team mode: APPROVED sources + caller's own PENDING/REJECTED submissions.
     * Personal: owned or shared with email.
     */
    @Transactional(readOnly = true)
    @Override
    public List<KnowledgeSource> listAccessible(OrgContext ctx) {
        if (ctx == null || ctx.email() == null) return repo.findAllByOrderByIngestedAtDesc();
        if (ctx.isTeam()) return repo.findByOrgIdForMember(ctx.orgId(), ctx.email());
        return repo.findAccessibleByEmail(ctx.email());
    }

    @Transactional(readOnly = true)
    @Override
    public List<KnowledgeSource> listAccessible(String email) {
        if (email == null) return repo.findAllByOrderByIngestedAtDesc();
        return repo.findAccessibleByEmail(email);
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
        String callerEmail = ctx != null ? ctx.email() : null;
        String orgId       = ctx != null && ctx.isTeam() ? ctx.orgId() : null;
        var optional = orgId != null ? repo.findBySourceAndOrgId(source, orgId) : repo.findBySource(source);
        optional.ifPresent(ks -> {
            if (!ctx.isTeam() && callerEmail != null && ks.getOwnerEmail() != null
                    && !ks.getOwnerEmail().equalsIgnoreCase(callerEmail)) {
                throw new SecurityException("Only the owner can delete this source.");
            }
            ingestionService.deleteBySource(source);
            repo.delete(ks);
            log.info("[KnowledgeSourceService] Deleted source='{}' by {}", source, callerEmail);
        });
    }

    @Transactional
    @Override
    public void delete(String source, String callerEmail) {
        delete(source, new OrgContext(callerEmail, "PERSONAL", null));
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
        String callerEmail = ctx != null ? ctx.email() : null;
        if (!ctx.isTeam() && callerEmail != null && ks.getOwnerEmail() != null
                && !ks.getOwnerEmail().equalsIgnoreCase(callerEmail)) {
            throw new SecurityException("Only the owner can edit this source.");
        }
        if (label    != null) ks.setLabel(label.isBlank()       ? null : label.trim());
        if (category != null) ks.setCategory(category.isBlank() ? null : category.trim());
        return repo.save(ks);
    }

    @Transactional
    @Override
    public KnowledgeSource updateMetadata(String source, String label, String category, String callerEmail) {
        return updateMetadata(source, label, category, new OrgContext(callerEmail, "PERSONAL", null));
    }

    /**
     * Replace the shared-email list. Not applicable in team mode (org-level sharing).
     */
    @Transactional
    @Override
    public KnowledgeSource updateSharing(String source, List<String> emails, String callerEmail) {
        KnowledgeSource ks = repo.findBySource(source)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + source));

        if (callerEmail != null && ks.getOwnerEmail() != null
                && !ks.getOwnerEmail().equalsIgnoreCase(callerEmail)) {
            throw new SecurityException("Only the owner can change sharing.");
        }

        ks.getShares().clear();
        emails.stream()
                .map(String::trim)
                .filter(e -> !e.isBlank())
                .distinct()
                .forEach(e -> ks.getShares().add(new KnowledgeSourceShare(ks, e)));

        return repo.save(ks);
    }
}
