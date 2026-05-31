package com.ragagent.connector;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "connector_oauth_states")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectorOAuthState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String state;

    private String ownerEmail;
    private String provider;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
