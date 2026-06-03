package com.ragagent.workflow.repository;

import com.ragagent.workflow.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRepository extends JpaRepository<Workflow, String> {

    /** Personal mode: workflows owned by this email with no org. */
    List<Workflow> findByOwnerEmailAndOrgIdIsNullOrderByUpdatedAtDesc(String ownerEmail);

    /** Team mode: workflows belonging to this org. */
    List<Workflow> findByOrgIdOrderByUpdatedAtDesc(String orgId);

    /** Backward-compatible alias used internally when mode is unspecified. */
    default List<Workflow> findByOwnerEmailOrderByUpdatedAtDesc(String ownerEmail) {
        return findByOwnerEmailAndOrgIdIsNullOrderByUpdatedAtDesc(ownerEmail);
    }
}
