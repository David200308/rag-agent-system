package com.ragagent.skill.repository;

import com.ragagent.skill.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, String> {

    List<Skill> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail);

    List<Skill> findAllByOrderByCreatedAtDesc();
}
