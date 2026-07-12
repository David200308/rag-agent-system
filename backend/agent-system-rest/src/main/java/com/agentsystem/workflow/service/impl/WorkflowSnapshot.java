package com.agentsystem.workflow.service.impl;

import com.agentsystem.workflow.entity.Workflow;
import com.agentsystem.workflow.entity.WorkflowAgent;

import java.util.List;

/** Serializable snapshot of a workflow's full config, stored as workflow_versions.snapshot_json. */
record WorkflowSnapshot(
        Workflow.AgentPattern agentPattern,
        Workflow.TeamExecMode teamExecMode,
        List<SnapshotAgent> agents,
        List<SnapshotEdge> edges
) {
    /** Agents referenced by array index (position in {@code agents}) rather than DB id, since ids are regenerated on restore. */
    record SnapshotAgent(
            String name,
            WorkflowAgent.AgentRole role,
            WorkflowAgent.NodeKind nodeKind,
            String conditionExpr,
            String outputSchemaJson,
            String systemPrompt,
            String toolsJson,
            String skillIdsJson,
            int orderIndex,
            double posX,
            double posY
    ) {}

    record SnapshotEdge(int sourceIndex, int targetIndex, String branchLabel) {}
}
