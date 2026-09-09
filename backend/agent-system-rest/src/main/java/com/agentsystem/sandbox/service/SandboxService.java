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
     * Writes bytes to a file at the given absolute path inside the container,
     * creating parent directories as needed. No-op if the container is unavailable.
     */
    void writeFile(String containerId, String path, byte[] content);

    /**
     * Wipes /workspace and kills background processes so the container can be
     * reused for a chained task without leaving stale state.
     */
    void recycleSandbox(String containerId);

    /**
     * Stops and removes the container, then releases the concurrency slot.
     * Skips the docker rm if the watchdog already removed it, and skips the slot
     * release if it was already released by {@link #suspend}.
     */
    void destroySandbox(String containerId);

    /**
     * Docker-stops (not removes) the container and releases its concurrency slot so
     * other runs can use it — the container and its /workspace filesystem are kept
     * intact for {@link #resume}. Used when a run is idling for a long time (e.g.
     * waiting on human input) so it doesn't hold a slot or burn resources.
     */
    void suspend(String containerId);

    /**
     * Reverses {@link #suspend}: re-acquires a concurrency slot (blocks if all slots
     * are taken — the same backpressure a fresh sandbox creation would hit) and
     * docker-starts the container again with its prior /workspace state intact.
     */
    void resume(String runId, String containerId);

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
