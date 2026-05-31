package com.ragagent.workflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "workflow_run_logs")
@Getter
@Setter
@NoArgsConstructor
public class WorkflowRunLog {

    public enum LogType { TOOL_CALL, TOOL_RESULT, LLM_RESPONSE, DELEGATION, ERROR, SYSTEM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "agent_name")
    private String agentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "log_type", nullable = false, length = 30)
    private LogType logType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public WorkflowRunLog(String runId, Long agentId, String agentName, LogType logType, String content) {
        this.runId = runId;
        this.agentId = agentId;
        this.agentName = agentName;
        this.logType = logType;
        this.content = content;
    }
}
