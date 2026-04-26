package com.ragagent.workflow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "workflow_agents")
@Getter
@Setter
@NoArgsConstructor
public class WorkflowAgent {

    public enum AgentRole { MAIN, SUB, PEER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false, length = 36)
    private String workflowId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentRole role;

    @Column(nullable = false)
    private String name;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    /** JSON array of enabled tool names, e.g. ["BASH","GIT","CURL"]. */
    @Column(name = "tools_json", columnDefinition = "TEXT")
    private String toolsJson = "[]";

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    @Column(name = "pos_x", nullable = false)
    private double posX = 0;

    @Column(name = "pos_y", nullable = false)
    private double posY = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
