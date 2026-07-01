package com.agentsystem.workflow.repository;

import com.agentsystem.workflow.entity.WorkflowRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, String> {

    /** All runs for a workflow regardless of owner (used internally). */
    List<WorkflowRun> findByWorkflowIdOrderByStartedAtDesc(String workflowId);

    /** Paginated runs for a workflow. */
    Page<WorkflowRun> findByWorkflowIdOrderByStartedAtDesc(String workflowId, Pageable pageable);

    /** Runs for a specific owner — used to scope to personal or team context. */
    List<WorkflowRun> findByWorkflowIdAndOwnerEmailOrderByStartedAtDesc(String workflowId, String ownerEmail);

    /** Personal mode runs: owner + no org. */
    List<WorkflowRun> findByOwnerEmailAndOrgIdIsNullOrderByStartedAtDesc(String ownerEmail);

    /** Team mode runs: owner + org (still private per user). */
    List<WorkflowRun> findByOwnerEmailAndOrgIdOrderByStartedAtDesc(String ownerEmail, String orgId);
}
