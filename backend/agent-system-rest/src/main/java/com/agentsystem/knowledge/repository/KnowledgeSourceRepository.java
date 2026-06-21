package com.agentsystem.knowledge.repository;

import com.agentsystem.knowledge.entity.KnowledgeSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSource, Long> {

    List<KnowledgeSource> findAllByOrderByIngestedAtDesc();

    Optional<KnowledgeSource> findBySource(String source);

    void deleteBySource(String source);

    /**
     * Personal mode: sources owned by or shared with the given email.
     */
    @Query("""
        SELECT DISTINCT ks FROM KnowledgeSource ks
        LEFT JOIN ks.shares sh
        WHERE ks.orgId IS NULL AND (ks.ownerEmail = :email OR sh.sharedEmail = :email)
        ORDER BY ks.ingestedAt DESC
        """)
    List<KnowledgeSource> findAccessibleByEmail(@Param("email") String email);

    /**
     * Team mode UI: approved sources + the caller's own pending/rejected submissions.
     */
    @Query("""
        SELECT ks FROM KnowledgeSource ks
        WHERE ks.orgId = :orgId
          AND (ks.status = 'APPROVED' OR ks.ownerEmail = :callerEmail)
        ORDER BY ks.ingestedAt DESC
        """)
    List<KnowledgeSource> findByOrgIdForMember(@Param("orgId") String orgId,
                                               @Param("callerEmail") String callerEmail);

    /**
     * Team mode agent retrieval: approved sources only.
     */
    @Query("""
        SELECT ks FROM KnowledgeSource ks
        WHERE ks.orgId = :orgId AND ks.status = 'APPROVED'
        ORDER BY ks.ingestedAt DESC
        """)
    List<KnowledgeSource> findApprovedByOrgId(@Param("orgId") String orgId);

    /**
     * Owner approval queue: all pending sources for the org.
     */
    @Query("""
        SELECT ks FROM KnowledgeSource ks
        WHERE ks.orgId = :orgId AND ks.status = 'PENDING'
        ORDER BY ks.ingestedAt DESC
        """)
    List<KnowledgeSource> findPendingByOrgId(@Param("orgId") String orgId);

    Optional<KnowledgeSource> findBySourceAndOrgId(String source, String orgId);
}
