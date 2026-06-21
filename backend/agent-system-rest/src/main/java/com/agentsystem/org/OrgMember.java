package com.agentsystem.org;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "org_members")
@Getter
@Setter
@NoArgsConstructor
@IdClass(OrgMemberId.class)
public class OrgMember {

    public enum Role { OWNER, MEMBER }

    @Id
    @Column(name = "org_id", length = 100)
    private String orgId;

    @Id
    @Column(length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.MEMBER;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    public OrgMember(String orgId, String email, Role role) {
        this.orgId    = orgId;
        this.email    = email;
        this.role     = role;
        this.joinedAt = Instant.now();
    }
}
