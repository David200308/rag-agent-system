package com.ragagent.skill.repository;

import com.ragagent.skill.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, String> {

    /** Personal mode: skills owned by this email. */
    List<Skill> findByOwnerEmailAndOrgIdIsNullOrderByCreatedAtDesc(String ownerEmail);

    /** Team mode UI: approved skills + caller's own pending/rejected. */
    @org.springframework.data.jpa.repository.Query("""
        SELECT s FROM Skill s
        WHERE s.orgId = :orgId
          AND (s.status = 'APPROVED' OR s.ownerEmail = :callerEmail)
        ORDER BY s.createdAt DESC
        """)
    List<Skill> findByOrgIdForMember(@org.springframework.data.repository.query.Param("orgId") String orgId,
                                     @org.springframework.data.repository.query.Param("callerEmail") String callerEmail);

    /** Team mode agent use: approved skills only. */
    @org.springframework.data.jpa.repository.Query("""
        SELECT s FROM Skill s
        WHERE s.orgId = :orgId AND s.status = 'APPROVED'
        ORDER BY s.createdAt DESC
        """)
    List<Skill> findApprovedByOrgId(@org.springframework.data.repository.query.Param("orgId") String orgId);

    /** Owner approval queue: all pending skills for the org. */
    @org.springframework.data.jpa.repository.Query("""
        SELECT s FROM Skill s
        WHERE s.orgId = :orgId AND s.status = 'PENDING'
        ORDER BY s.createdAt DESC
        """)
    List<Skill> findPendingByOrgId(@org.springframework.data.repository.query.Param("orgId") String orgId);

    List<Skill> findAllByOrderByCreatedAtDesc();

    /** Backward-compatible alias. */
    default List<Skill> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail) {
        return findByOwnerEmailAndOrgIdIsNullOrderByCreatedAtDesc(ownerEmail);
    }
}
