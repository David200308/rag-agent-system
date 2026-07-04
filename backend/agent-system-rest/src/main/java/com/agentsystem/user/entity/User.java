package com.agentsystem.user.entity;

import com.agentsystem.user.crypto.EmailAttributeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Replaces the old email_whitelist table. {@code email} is encrypted at rest (see
 * {@link EmailAttributeConverter}) — the column holds ciphertext, not plaintext.
 * New rows start at {@link UserStatus#PRE_USER}; an admin manually flips {@code status}
 * to {@link UserStatus#USER} directly in the database to grant access.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String uuid;

    @Convert(converter = EmailAttributeConverter.class)
    @Column(nullable = false, unique = true, length = 512)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.PRE_USER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public User(String uuid, String email, UserStatus status, boolean enabled) {
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
