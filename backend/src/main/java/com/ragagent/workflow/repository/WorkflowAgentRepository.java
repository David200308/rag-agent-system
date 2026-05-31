package com.ragagent.workflow.repository;

import com.ragagent.workflow.entity.WorkflowAgent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowAgentRepository extends JpaRepository<WorkflowAgent, Long> {
    List<WorkflowAgent> findByWorkflowIdOrderByOrderIndex(String workflowId);
    void deleteByWorkflowId(String workflowId);
}
