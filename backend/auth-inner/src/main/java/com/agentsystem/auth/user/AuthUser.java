package com.agentsystem.auth.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Maps the {@code users} table owned by agent-system-rest's {@code user} package.
 * auth-inner only reads/writes the columns login flows need — the full user lifecycle
 * (profile, admin, org display, etc.) stays owned by agent-system-rest; this entity
 * does not appear in this module's schema.sql (no CREATE TABLE — the table already
 * exists in the shared database).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class AuthUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String uuid;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 20)
    private String status = "PRE_USER";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public AuthUser(String uuid, String email, String status, boolean enabled) {
        this.uuid    = uuid;
        this.email   = email;
        this.status  = status;
        this.enabled = enabled;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
