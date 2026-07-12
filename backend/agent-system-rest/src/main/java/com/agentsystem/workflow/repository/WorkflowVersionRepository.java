package com.agentsystem.workflow.repository;

import com.agentsystem.workflow.entity.WorkflowVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, Long> {
    List<WorkflowVersion> findByWorkflowIdOrderByVersionNumberDesc(String workflowId);
    Optional<WorkflowVersion> findByWorkflowIdAndVersionNumber(String workflowId, int versionNumber);
    Optional<WorkflowVersion> findTopByWorkflowIdOrderByVersionNumberDesc(String workflowId);
}
