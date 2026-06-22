package com.agentsystem.workflow.service;

import com.agentsystem.workflow.entity.WorkflowRun;
import com.agentsystem.workflow.entity.WorkflowRunLog;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface WorkflowRunService {

    /**
     * Creates a WorkflowRun record and starts async execution. Returns the runId.
     */
    String startRun(String workflowId, String userInput, String ownerEmail, boolean emailNotify);

    /**
     * Opens an SSE stream for a run. The caller must hold the connection open.
     * Historical logs are replayed first, then live events streamed.
     */
    SseEmitter streamLogs(String runId);

    List<WorkflowRunLog> getLogs(String runId);

    List<WorkflowRun> getRuns(String workflowId);
}
