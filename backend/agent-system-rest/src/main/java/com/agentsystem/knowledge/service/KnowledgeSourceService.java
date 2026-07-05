package com.agentsystem.knowledge.service;

import com.agentsystem.knowledge.entity.KnowledgeSource;
import com.agentsystem.org.OrgContext;

import java.util.List;

public interface KnowledgeSourceService {

    /**
     * Record (or update) a source after successful ingestion.
     * In team mode, new uploads are set to PENDING until an owner approves.
     * Resolves ownerEmail to a user_uuid internally (bridge for email-only callers).
     */
    void upsert(String source, String label, String category, int chunkCount,
                String ownerEmail, String orgId);

    /** Backward-compatible overload for personal mode callers. */
    void upsert(String source, String label, String category, int chunkCount, String ownerEmail);

    /** Resolves ownership directly from ctx.userUuid()/ctx.orgId() — no email bridge needed. */
    void upsert(String source, String label, String category, int chunkCount, OrgContext ctx);

    /**
     * List sources visible given the org context (for management UI).
     * Team mode: APPROVED sources + caller's own PENDING/REJECTED submissions.
     * Personal: owned or shared with the caller.
     */
    List<KnowledgeSource> listAccessible(OrgContext ctx);

    /** Resolves email to a user_uuid internally (bridge for email-only callers). */
    List<KnowledgeSource> listAccessible(String email);

    /** All PENDING sources for the org (owner approval queue). */
    List<KnowledgeSource> listPendingByOrg(String orgId);

    /** Approve a pending knowledge source (owner only — caller must enforce ownership). */
    KnowledgeSource approve(Long id);

    /** Reject a pending knowledge source: sets REJECTED and removes from Weaviate. */
    void reject(Long id);

    /**
     * Delete a source. Team mode: any org member may delete.
     * Personal mode: only the owner.
     */
    void delete(String source, OrgContext ctx);

    /** Resolves callerEmail to a user_uuid internally (bridge for email-only callers). */
    void delete(String source, String callerEmail);

    /**
     * Update label and/or category. Team mode: any org member may edit.
     * Personal mode: only the owner.
     */
    KnowledgeSource updateMetadata(String source, String label, String category, OrgContext ctx);

    /** Resolves callerEmail to a user_uuid internally (bridge for email-only callers). */
    KnowledgeSource updateMetadata(String source, String label, String category, String callerEmail);

    /**
     * Replace the share-target list. Not applicable in team mode (org-level sharing).
     *
     * @param emails share-target emails; only entries that already belong to a registered
     *               user are resolved and stored (as uuids) — an unregistered email is
     *               silently dropped, since it can't be granted sharing access without an account
     */
    KnowledgeSource updateSharing(String source, List<String> emails, String callerEmail);

    /** Resolves ownership directly from ctx.userUuid() — no email bridge needed. */
    KnowledgeSource updateSharing(String source, List<String> emails, OrgContext ctx);
}
