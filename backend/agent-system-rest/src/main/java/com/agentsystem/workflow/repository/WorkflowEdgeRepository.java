package com.agentsystem.workflow.repository;

import com.agentsystem.workflow.entity.WorkflowEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowEdgeRepository extends JpaRepository<WorkflowEdge, Long> {
    List<WorkflowEdge> findByWorkflowId(String workflowId);
    void deleteByWorkflowId(String workflowId);
}
