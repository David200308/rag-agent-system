package com.ragagent.knowledge;

import com.ragagent.knowledge.entity.KnowledgeSource;
import com.ragagent.knowledge.entity.KnowledgeSourceShare;
import com.ragagent.knowledge.repository.KnowledgeSourceRepository;
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
    public void upsert(String source, String label, String category, int chunkCount, String ownerEmail) {
        repo.findBySource(source).ifPresentOrElse(
                existing -> {
                    existing.setLabel(label);
                    existing.setCategory(category);
                    existing.setChunkCount(chunkCount);
                    // Preserve original owner on re-ingest
                    if (existing.getOwnerEmail() == null && ownerEmail != null) {
                        existing.setOwnerEmail(ownerEmail);
                    }
                    repo.save(existing);
                },
                () -> repo.save(new KnowledgeSource(source, label, category, chunkCount, ownerEmail))
        );
    }

    /**
     * List sources visible to the given email.
     * When auth is disabled (email == null), all sources are returned.
     */
    @Transactional(readOnly = true)
    public List<KnowledgeSource> listAccessible(String email) {
        if (email == null) {
            return repo.findAllByOrderByIngestedAtDesc();
        }
        return repo.findAccessibleByEmail(email);
    }

    /**
     * Delete a source — removes Weaviate chunks and the MySQL row.
     * Only the owner may delete; throws if the caller is not the owner.
     */
    @Transactional
    public void delete(String source, String callerEmail) {
        repo.findBySource(source).ifPresent(ks -> {
            if (callerEmail != null && ks.getOwnerEmail() != null
                    && !ks.getOwnerEmail().equalsIgnoreCase(callerEmail)) {
                throw new SecurityException("Only the owner can delete this source.");
            }
            ingestionService.deleteBySource(source);
            repo.deleteBySource(source);
            log.info("[KnowledgeSourceService] Deleted source='{}' by {}", source, callerEmail);
        });
    }

    /**
     * Update label and/or category for a source.
     * Only the owner may edit metadata.
     */
    @Transactional
    public KnowledgeSource updateMetadata(String source, String label, String category, String callerEmail) {
        KnowledgeSource ks = repo.findBySource(source)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + source));

        if (callerEmail != null && ks.getOwnerEmail() != null
                && !ks.getOwnerEmail().equalsIgnoreCase(callerEmail)) {
            throw new SecurityException("Only the owner can edit this source.");
        }

        if (label    != null) ks.setLabel(label.isBlank()    ? null : label.trim());
        if (category != null) ks.setCategory(category.isBlank() ? null : category.trim());

        return repo.save(ks);
    }

    /**
     * Replace the shared-email list for a source.
     * Only the owner may change sharing.
     */
    @Transactional
    public KnowledgeSource updateSharing(String source, List<String> emails, String callerEmail) {
        KnowledgeSource ks = repo.findBySource(source)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + source));

        if (callerEmail != null && ks.getOwnerEmail() != null
                && !ks.getOwnerEmail().equalsIgnoreCase(callerEmail)) {
            throw new SecurityException("Only the owner can change sharing.");
        }

        // Replace share list
        ks.getShares().clear();
        emails.stream()
                .map(String::trim)
                .filter(e -> !e.isBlank())
                .distinct()
                .forEach(e -> ks.getShares().add(new KnowledgeSourceShare(ks, e)));

        return repo.save(ks);
    }
}
