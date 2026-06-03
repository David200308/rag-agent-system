package com.ragagent.skill.repository;

import com.ragagent.skill.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, String> {

    /** Personal mode: skills owned by this email. */
    List<Skill> findByOwnerEmailAndOrgIdIsNullOrderByCreatedAtDesc(String ownerEmail);

    /** Team mode: skills belonging to this org. */
    List<Skill> findByOrgIdOrderByCreatedAtDesc(String orgId);

    List<Skill> findAllByOrderByCreatedAtDesc();

    /** Backward-compatible alias. */
    default List<Skill> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail) {
        return findByOwnerEmailAndOrgIdIsNullOrderByCreatedAtDesc(ownerEmail);
    }
}
