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
     * Return all sources accessible to a given email:
     * either owned by them, or explicitly shared with them.
     */
    @Query("""
        SELECT DISTINCT ks FROM KnowledgeSource ks
        LEFT JOIN ks.shares sh
        WHERE ks.ownerEmail = :email OR sh.sharedEmail = :email
        ORDER BY ks.ingestedAt DESC
        """)
    List<KnowledgeSource> findAccessibleByEmail(@Param("email") String email);
}
