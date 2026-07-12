package com.agentsystem.workflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A named snapshot of a workflow's full configuration (pattern, agents, edges),
 * saved explicitly by the user. Runs record which version was active when they
 * started; restoring an older version creates a new version on top rather than
 * deleting history.
 */
@Entity
@Table(name = "workflow_versions")
@Getter
@Setter
@NoArgsConstructor
public class WorkflowVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false, length = 36)
    private String workflowId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(length = 255)
    private String label;

    /** Serialized WorkflowServiceImpl.Snapshot (pattern, teamExecMode, agents, edges). */
    @Column(name = "snapshot_json", columnDefinition = "LONGTEXT", nullable = false)
    private String snapshotJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
