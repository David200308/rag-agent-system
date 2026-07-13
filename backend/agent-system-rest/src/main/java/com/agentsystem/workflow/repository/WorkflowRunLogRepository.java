package com.agentsystem.workflow.repository;

import com.agentsystem.workflow.entity.WorkflowRunLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRunLogRepository extends JpaRepository<WorkflowRunLog, Long> {
    List<WorkflowRunLog> findByRunIdOrderByCreatedAt(String runId);

    void deleteByRunId(String runId);
}
