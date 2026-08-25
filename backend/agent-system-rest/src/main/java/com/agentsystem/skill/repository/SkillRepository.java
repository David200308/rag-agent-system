package com.agentsystem.skill.repository;

import com.agentsystem.skill.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, String> {

    /** Personal mode: skills owned by this uuid. */
    List<Skill> findByOwnerUuidAndOrgIdIsNullOrderByCreatedAtDesc(String ownerUuid);

    /** Team mode UI: skills with an approved version + caller's own submissions (even if still pending). */
    @Query("""
        SELECT s FROM Skill s
        WHERE s.orgId = :orgId
          AND (s.ownerUuid = :callerUuid
               OR EXISTS (SELECT 1 FROM SkillVersion v WHERE v.skillId = s.id AND v.status = 'APPROVED'))
        ORDER BY s.createdAt DESC
        """)
    List<Skill> findByOrgIdForMember(@Param("orgId") String orgId,
                                     @Param("callerUuid") String callerUuid);

    /** Team mode agent use: skills with at least one approved version. */
    @Query("""
        SELECT s FROM Skill s
        WHERE s.orgId = :orgId
          AND EXISTS (SELECT 1 FROM SkillVersion v WHERE v.skillId = s.id AND v.status = 'APPROVED')
        ORDER BY s.createdAt DESC
        """)
    List<Skill> findApprovedByOrgId(@Param("orgId") String orgId);

    List<Skill> findAllByOrderByCreatedAtDesc();

    /** Backward-compatible alias. */
    default List<Skill> findByOwnerUuidOrderByCreatedAtDesc(String ownerUuid) {
        return findByOwnerUuidAndOrgIdIsNullOrderByCreatedAtDesc(ownerUuid);
    }
}
