package com.agentsystem.auth.org;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Read-only mirror of agent-system-rest's org/entity/Organization — maps the shared
 * {@code organizations} table. Full org CRUD/admin stays owned by agent-system-rest's
 * org/ package; auth-inner only checks existence during TEAM-mode login.
 */
@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
public class OrgOrganization {

    @Id
    @Column(name = "org_id", length = 100)
    private String orgId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
