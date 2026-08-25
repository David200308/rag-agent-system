package com.agentsystem.workflow.entity;

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

    /** Only meaningful for the GRAPH pattern; other patterns implicitly treat every node as AGENT. */
    public enum NodeKind { AGENT, CONDITION, END }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false, length = 36)
    private String workflowId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_kind", nullable = false, length = 20)
    private NodeKind nodeKind = NodeKind.AGENT;

    /** Branch-selection instructions for CONDITION nodes; the run engine asks the LLM to pick an outgoing edge's label based on this. */
    @Column(name = "condition_expr", columnDefinition = "TEXT")
    private String conditionExpr;

    /** Optional JSON Schema (subset — type/properties/required/enum/items) the agent's final answer must satisfy. */
    @Column(name = "output_schema_json", columnDefinition = "TEXT")
    private String outputSchemaJson;

    @Column(nullable = false)
    private String name;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    /** JSON array of enabled tool names, e.g. ["BASH","GIT","CURL"]. */
    @Column(name = "tools_json", columnDefinition = "TEXT")
    private String toolsJson = "[]";

    /** JSON array of attached skill IDs, e.g. ["uuid1","uuid2"]. */
    @Column(name = "skill_ids_json", columnDefinition = "TEXT")
    private String skillIdsJson = "[]";

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    @Column(name = "pos_x", nullable = false)
    private double posX = 0;

    @Column(name = "pos_y", nullable = false)
    private double posY = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
