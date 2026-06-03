package com.ragagent.knowledge.repository;

import com.ragagent.knowledge.entity.KnowledgeSource;
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
     * Team mode: all sources belonging to the given org.
     */
    @Query("""
        SELECT ks FROM KnowledgeSource ks
        WHERE ks.orgId = :orgId
        ORDER BY ks.ingestedAt DESC
        """)
    List<KnowledgeSource> findByOrgId(@Param("orgId") String orgId);

    Optional<KnowledgeSource> findBySourceAndOrgId(String source, String orgId);
}
