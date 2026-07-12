package com.agentsystem.knowledge.dto;

import java.time.Instant;
import java.util.List;

/**
 * API-facing projection of KnowledgeSource. The entity stores owner/share identity as
 * uuids (see KnowledgeSource.ownerUuid / KnowledgeSourceShare.sharedUuid), but the
 * frontend is email-based throughout — this resolves uuids to emails at the controller
 * boundary instead of leaking uuids into a response shape the UI wasn't built for.
 */
public record KnowledgeSourceView(
        Long id,
        String source,
        String label,
        String category,
        int chunkCount,
        String ownerEmail,
        Instant ingestedAt,
        List<ShareView> shares
) {
    public record ShareView(Long id, String sharedEmail) {}
}
