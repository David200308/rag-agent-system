package com.agentsystem.sandbox.service;

import java.util.function.Consumer;

public interface SandboxService {

    String createSandbox(String runId, Consumer<String> logger);

    String createSandboxWithNetwork(String runId, Consumer<String> logger);

    /**
     * Executes a shell command inside the container.
     *
     * @throws SandboxKilledException if the watchdog terminated this container
     *         due to excessive resource usage — callers should let this propagate
     *         to fail the workflow run.
     */
    String exec(String containerId, String command);

    /**
     * Wipes /workspace and kills background processes so the container can be
     * reused for a chained task without leaving stale state.
     */
    void recycleSandbox(String containerId);

    /**
     * Stops and removes the container, then releases the concurrency slot.
     * Skips the docker rm if the watchdog already removed it.
     */
    void destroySandbox(String containerId);

    SandboxStatus status();

    record SandboxStatus(int maxConcurrent, int active, int queued, int queueCapacity) {
        public boolean atCapacity() { return active >= maxConcurrent; }
    }

    class SandboxQueueFullException extends RuntimeException {
        public SandboxQueueFullException(String msg) { super(msg); }
    }

    class SandboxResourceException extends RuntimeException {
        public SandboxResourceException(String msg) { super(msg); }
    }

    class SandboxStartupException extends RuntimeException {
        public SandboxStartupException(String msg) { super(msg); }
    }

    class SandboxKilledException extends RuntimeException {
        public SandboxKilledException(String msg) { super(msg); }
    }
}
