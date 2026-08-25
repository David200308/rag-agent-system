package com.agentsystem.workflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Explicit node-to-node connection, used only by the GRAPH pattern.
 * branchLabel is set on edges leaving a CONDITION node (must match a label the
 * run engine's classifier can choose) and is null on plain agent → agent edges.
 */
@Entity
@Table(name = "workflow_edges")
@Getter
@Setter
@NoArgsConstructor
public class WorkflowEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false, length = 36)
    private String workflowId;

    @Column(name = "source_node_id", nullable = false)
    private Long sourceNodeId;

    @Column(name = "target_node_id", nullable = false)
    private Long targetNodeId;

    @Column(name = "branch_label", length = 100)
    private String branchLabel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
