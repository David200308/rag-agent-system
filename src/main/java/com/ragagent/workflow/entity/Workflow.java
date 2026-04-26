package com.ragagent.workflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "workflows")
@Getter
@Setter
@NoArgsConstructor
public class Workflow {

    public enum AgentPattern { ORCHESTRATOR, TEAM }
    public enum TeamExecMode  { PARALLEL, SEQUENTIAL }

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "owner_email")
    private String ownerEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_pattern", nullable = false, length = 20)
    private AgentPattern agentPattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_exec_mode", length = 20)
    private TeamExecMode teamExecMode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Workflow(String id, String name, String ownerEmail, AgentPattern pattern) {
        this.id = id;
        this.name = name;
        this.ownerEmail = ownerEmail;
        this.agentPattern = pattern;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
