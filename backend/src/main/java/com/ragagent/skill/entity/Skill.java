package com.ragagent.skill.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

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

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_type", length = 16)
    private String fileType;

    @Column(nullable = false)
    private long size;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Skill(String id, String ownerEmail, String name, String fileName,
                 String fileType, long size, String content) {
        this.id         = id;
        this.ownerEmail = ownerEmail;
        this.name       = name;
        this.fileName   = fileName;
        this.fileType   = fileType;
        this.size       = size;
        this.content    = content;
        this.createdAt  = Instant.now();
    }
}
