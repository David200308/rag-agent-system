package com.agentsystem.skill.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Pure identity/ownership record. File content and per-upload metadata
 * (file name/type/size, approval status) live on {@link SkillVersion} —
 * every upload (create or replace) is a new immutable version.
 */
@Entity
@Table(name = "skills")
@Getter
@Setter
@NoArgsConstructor
public class Skill {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "owner_email", length = 255)
    private String ownerEmail;

    /** Org slug when in team mode; null = personal. */
    @Column(name = "org_id", length = 100)
    private String orgId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Skill(String id, String ownerEmail, String name) {
        this.id         = id;
        this.ownerEmail = ownerEmail;
        this.name       = name;
        this.createdAt  = Instant.now();
    }
}
