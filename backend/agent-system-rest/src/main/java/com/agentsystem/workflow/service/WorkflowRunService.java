package com.agentsystem.workflow.service;

import com.agentsystem.workflow.entity.WorkflowRun;
import com.agentsystem.workflow.entity.WorkflowRunLog;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface WorkflowRunService {

    /**
     * Creates a WorkflowRun record and starts async execution. Returns the runId.
     */
    String startRun(String workflowId, String userInput, String ownerEmail, boolean emailNotify);

    /** Same as above, with files attached at the start of the run (written into the sandbox before agents run). */
    String startRun(String workflowId, String userInput, String ownerEmail, boolean emailNotify, List<MultipartFile> attachments);

    /** Resolves ownership directly from ownerUuid — no email bridge needed. */
    String startRunByUuid(String workflowId, String userInput, String ownerUuid, boolean emailNotify);

    /** Same as above, with files attached at the start of the run. */
    String startRunByUuid(String workflowId, String userInput, String ownerUuid, boolean emailNotify, List<MultipartFile> attachments);

    /**
     * Answers a run that is paused in AWAITING_INPUT on a text question (ASK_USER tool,
     * kind=TEXT), resuming its ReAct loop.
     *
     * @throws IllegalStateException if the run is not currently awaiting a text answer.
     * @throws SecurityException if callerUuid is set and does not own the run.
     */
    void answerRun(String runId, String answerText, String callerUuid);

    /**
     * Answers a run that is paused in AWAITING_INPUT on a file request (ASK_USER tool,
     * kind=FILE) — the file is written into the run's sandbox and its path handed back
     * to the agent as the tool result.
     *
     * @throws IllegalStateException if the run is not currently awaiting a file answer.
     * @throws SecurityException if callerUuid is set and does not own the run.
     */
    void answerRunFile(String runId, MultipartFile file, String callerUuid);

    /**
     * Wakes a SUSPENDED run back up: resumes its sandbox (docker start, re-acquiring a
     * concurrency slot) and returns it to AWAITING_INPUT so the pending question is shown
     * again. No-op on the ReAct loop itself — it's still parked on the same answer future.
     *
     * @throws IllegalStateException if the run is not currently suspended.
     * @throws SecurityException if callerUuid is set and does not own the run.
     */
    void recoverRun(String runId, String callerUuid);

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
