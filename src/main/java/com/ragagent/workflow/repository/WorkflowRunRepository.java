package com.ragagent.workflow.repository;

import com.ragagent.workflow.entity.WorkflowRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, String> {
    List<WorkflowRun> findByWorkflowIdOrderByStartedAtDesc(String workflowId);
}
