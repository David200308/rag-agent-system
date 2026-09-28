package com.agentsystem.sandbox.service;

import com.agentsystem.sandbox.entity.PersistentSandbox;

import java.util.List;
import java.util.function.Consumer;

/**
 * Manages permanent, user-owned sandboxes launched from the Sandbox Management page.
 * Unlike {@link SandboxService}'s ephemeral per-run containers, these are only started,
 * stopped, restarted, cleared or removed by their owning user, and can be attached to a
 * workflow so its runs reuse the container instead of creating a new one each time.
 */
public interface PersistentSandboxService {

    List<PersistentSandbox> list(String ownerUuid);

    /** {used, max} sandboxes for this user. */
    record Quota(long used, int max) {}

    Quota quota(String ownerUuid);

    /**
     * Launches a new persistent sandbox for this user.
     *
     * @throws IllegalStateException if the user is already at their quota
     */
    PersistentSandbox create(String ownerUuid, String name, boolean network);

    /** Docker-stops the container, releasing its concurrency slot. */
    PersistentSandbox stop(String id, String ownerUuid);

    /** Restarts the container — stop+start if running, or just start if already stopped. */
    PersistentSandbox restart(String id, String ownerUuid);

    /** Wipes /workspace inside the container. Only valid while the sandbox is RUNNING. */
    void clear(String id, String ownerUuid);

    /** Destroys the container and deletes the record. Detaches any workflow using it (FK ON DELETE SET NULL). */
    void remove(String id, String ownerUuid);

    /**
     * Resolves a persistent sandbox for use by a workflow run — verifies the run's owner
     * matches the sandbox's owner and auto-resumes it if it was STOPPED.
     *
     * @throws SecurityException if {@code ownerUuid} doesn't own this sandbox
     * @throws IllegalArgumentException if the sandbox no longer exists
     */
    PersistentSandbox acquireForRun(String id, String ownerUuid, Consumer<String> logger);
}
