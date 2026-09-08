package com.agentsystem.auth.org;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Read-only mirror of agent-system-rest's org/entity/OrgMember — maps the shared {@code org_members} table. */
@Entity
@Table(name = "org_members")
@Getter
@Setter
@NoArgsConstructor
@IdClass(OrgMemberId.class)
public class OrgMember {

    @Id
    @Column(name = "org_id", length = 100)
    private String orgId;

    @Id
    @Column(name = "user_uuid", length = 36)
    private String userUuid;

    @Column(length = 255)
    private String email;

    @Column(nullable = false, length = 20)
    private String role = "MEMBER";

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();
}
