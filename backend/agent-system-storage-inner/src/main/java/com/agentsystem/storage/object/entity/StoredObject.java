package com.agentsystem.storage.object.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "stored_objects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StoredObject {

    @Id
    private String id;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "owner_uuid", nullable = false, length = 36)
    private String ownerUuid;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
