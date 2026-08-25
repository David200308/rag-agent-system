package com.agentsystem.skill.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "skill_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SkillVersion {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "skill_id", nullable = false, length = 36)
    private String skillId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    /** id of the object in agent-system-storage-inner holding this version's bytes. */
    @Column(name = "object_id", nullable = false, length = 36)
    private String objectId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_type", length = 16)
    private String fileType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Approval status: PENDING | APPROVED | REJECTED. Always APPROVED in personal mode. */
    @Column(nullable = false, length = 20)
    private String status = "APPROVED";

    @Column(name = "created_by_uuid", length = 36)
    private String createdByUuid;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
