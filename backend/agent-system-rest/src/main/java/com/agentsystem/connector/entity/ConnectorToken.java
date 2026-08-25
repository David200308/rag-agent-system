package com.agentsystem.connector.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "connector_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectorToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_uuid", length = 36)
    private String ownerUuid;
    private String provider;

    /** Org slug when in team mode; null = personal. */
    @Column(name = "org_id", length = 100)
    private String orgId;

    @Column(columnDefinition = "TEXT")
    private String accessToken;

    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    private String tokenType;

    @Column(columnDefinition = "TEXT")
    private String scope;

    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
