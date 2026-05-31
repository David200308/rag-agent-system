package com.ragagent.workflow.repository;

import com.ragagent.workflow.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRepository extends JpaRepository<Workflow, String> {
    List<Workflow> findByOwnerEmailOrderByUpdatedAtDesc(String ownerEmail);
}
