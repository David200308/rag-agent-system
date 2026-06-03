package com.ragagent.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "passkey_challenges",
       indexes = @Index(name = "idx_pkc_email_type", columnList = "email, type"))
@Getter
@Setter
@NoArgsConstructor
public class PasskeyChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 20)
    private String type; // REGISTER | AUTHENTICATE

    @Column(name = "request_json", nullable = false, columnDefinition = "TEXT")
    private String requestJson;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Login mode stored at challenge-begin time so finish can issue the correct JWT. */
    @Column(length = 20)
    private String mode = "PERSONAL";

    /** Org slug for TEAM mode; null for PERSONAL. */
    @Column(name = "org_id", length = 100)
    private String orgId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
