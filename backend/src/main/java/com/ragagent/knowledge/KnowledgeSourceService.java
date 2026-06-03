package com.ragagent.knowledge;

import com.ragagent.knowledge.entity.KnowledgeSource;
import com.ragagent.knowledge.entity.KnowledgeSourceShare;
import com.ragagent.knowledge.repository.KnowledgeSourceRepository;
import com.ragagent.org.OrgContext;
import com.ragagent.rag.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSourceService {

    private final KnowledgeSourceRepository repo;
    private final DocumentIngestionService  ingestionService;

    /** Record (or update) a source after successful ingestion. */
    @Transactional
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
                () -> repo.save(new KnowledgeSource(source, label, category, chunkCount, ownerEmail, orgId))
        );
    }

    /** Backward-compatible overload for personal mode callers. */
    @Transactional
    public void upsert(String source, String label, String category, int chunkCount, String ownerEmail) {
        upsert(source, label, category, chunkCount, ownerEmail, null);
    }

    /**
     * List sources visible given the org context.
     * Team mode: all org sources. Personal: owned or shared with email. No auth: all.
     */
    @Transactional(readOnly = true)
    public List<KnowledgeSource> listAccessible(OrgContext ctx) {
        if (ctx == null || ctx.email() == null) return repo.findAllByOrderByIngestedAtDesc();
        if (ctx.isTeam()) return repo.findByOrgId(ctx.orgId());
        return repo.findAccessibleByEmail(ctx.email());
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSource> listAccessible(String email) {
        if (email == null) return repo.findAllByOrderByIngestedAtDesc();
        return repo.findAccessibleByEmail(email);
    }

    /**
     * Delete a source. Team mode: any org member may delete.
     * Personal mode: only the owner.
     */
    @Transactional
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
    public void delete(String source, String callerEmail) {
        delete(source, new OrgContext(callerEmail, "PERSONAL", null));
    }

    /**
     * Update label and/or category. Team mode: any org member may edit.
     * Personal mode: only the owner.
     */
    @Transactional
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
    public KnowledgeSource updateMetadata(String source, String label, String category, String callerEmail) {
        return updateMetadata(source, label, category, new OrgContext(callerEmail, "PERSONAL", null));
    }

    /**
     * Replace the shared-email list. Not applicable in team mode (org-level sharing).
     */
    @Transactional
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
