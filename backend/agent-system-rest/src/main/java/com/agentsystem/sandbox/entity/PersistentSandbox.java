package com.agentsystem.sandbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A user-launched sandbox that persists across workflow runs. Unlike the ephemeral
 * containers {@code SandboxService} creates per-run, this one is only started, stopped,
 * restarted, cleared or removed by its owning user, and stays alive across runs when
 * attached to a workflow.
 */
@Entity
@Table(name = "persistent_sandboxes")
@Getter
@Setter
@NoArgsConstructor
public class PersistentSandbox {

    public enum Status { RUNNING, STOPPED }

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "owner_uuid", length = 36, nullable = false)
    private String ownerUuid;

    @Column(nullable = false)
    private String name;

    /** Null only in the brief window before the underlying container has been created. */
    @Column(name = "container_id", length = 128)
    private String containerId;

    @Column(name = "network_enabled", nullable = false)
    private boolean networkEnabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.RUNNING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public PersistentSandbox(String id, String ownerUuid, String name, boolean networkEnabled) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.name = name;
        this.networkEnabled = networkEnabled;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
