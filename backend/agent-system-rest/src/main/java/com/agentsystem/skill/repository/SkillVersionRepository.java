package com.agentsystem.skill.repository;

import com.agentsystem.skill.entity.SkillVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SkillVersionRepository extends JpaRepository<SkillVersion, String> {

    List<SkillVersion> findBySkillIdOrderByVersionNumberDesc(String skillId);

    Optional<SkillVersion> findTopBySkillIdOrderByVersionNumberDesc(String skillId);

    /** The version actually served to workflows/preview-as-current — latest APPROVED. */
    Optional<SkillVersion> findTopBySkillIdAndStatusOrderByVersionNumberDesc(String skillId, String status);

    Optional<SkillVersion> findBySkillIdAndVersionNumber(String skillId, int versionNumber);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM SkillVersion v WHERE v.skillId = :skillId")
    int findMaxVersionNumber(@Param("skillId") String skillId);

    /** Owner approval queue: all pending versions for skills in this org. */
    @Query("""
        SELECT v FROM SkillVersion v
        WHERE v.status = 'PENDING'
          AND v.skillId IN (SELECT s.id FROM Skill s WHERE s.orgId = :orgId)
        ORDER BY v.createdAt DESC
        """)
    List<SkillVersion> findPendingByOrgId(@Param("orgId") String orgId);
}
