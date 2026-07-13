package com.agentsystem.workflow.service;

import com.agentsystem.workflow.entity.WorkflowRun;
import com.agentsystem.workflow.entity.WorkflowRunLog;
import org.springframework.data.domain.Page;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface WorkflowRunService {

    /**
     * Creates a WorkflowRun record and starts async execution. Returns the runId.
     */
    String startRun(String workflowId, String userInput, String ownerEmail, boolean emailNotify);

    /** Resolves ownership directly from ownerUuid — no email bridge needed. */
    String startRunByUuid(String workflowId, String userInput, String ownerUuid, boolean emailNotify);

    /**
     * Opens an SSE stream for a run. The caller must hold the connection open.
     * Historical logs are replayed first, then live events streamed.
     */
    SseEmitter streamLogs(String runId);

    List<WorkflowRunLog> getLogs(String runId);

    Page<WorkflowRun> getRuns(String workflowId, int page, int size);

    /**
     * Cancels a running (or still-pending) run: tears down its sandbox, interrupts its
     * worker thread, and marks it CANCELLED. No-op if the run is already terminal.
     *
     * @throws SecurityException if callerUuid is set and does not own the run.
     */
    void cancelRun(String runId, String callerUuid);

    /**
     * Deletes a run and its logs. If the run is still active it is cancelled first.
     *
     * @throws SecurityException if callerUuid is set and does not own the run.
     */
    void deleteRun(String runId, String callerUuid);
}
