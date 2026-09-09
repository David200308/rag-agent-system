package com.agentsystem.workflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "workflow_runs")
@Getter
@Setter
@NoArgsConstructor
public class WorkflowRun {

    public enum RunStatus { PENDING, RUNNING, AWAITING_INPUT, SUSPENDED, DONE, FAILED, CANCELLED }

    public enum PendingKind { TEXT, FILE }

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "workflow_id", nullable = false, length = 36)
    private String workflowId;

    @Column(name = "owner_uuid", length = 36)
    private String ownerUuid;

    /** Org slug when in team mode; null = personal. */
    @Column(name = "org_id", length = 100)
    private String orgId;

    @Column(name = "user_input", columnDefinition = "TEXT", nullable = false)
    private String userInput;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunStatus status = RunStatus.PENDING;

    @Column(name = "sandbox_container", length = 128)
    private String sandboxContainer;

    @Column(name = "final_output", columnDefinition = "LONGTEXT")
    private String finalOutput;

    /** Version number of the workflow (from workflow_versions) active when this run started; null if no version has ever been saved. */
    @Column(name = "workflow_version")
    private Integer workflowVersion;

    /** Set while status=AWAITING_INPUT — the question an ASK_USER tool call is waiting on. */
    @Column(name = "pending_question", columnDefinition = "TEXT")
    private String pendingQuestion;

    @Enumerated(EnumType.STRING)
    @Column(name = "pending_kind", length = 10)
    private PendingKind pendingKind;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    public WorkflowRun(String id, String workflowId, String ownerUuid, String userInput) {
        this.id = id;
        this.workflowId = workflowId;
        this.ownerUuid = ownerUuid;
        this.userInput = userInput;
    }
}
