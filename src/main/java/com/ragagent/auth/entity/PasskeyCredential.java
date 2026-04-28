package com.ragagent.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "passkey_credentials",
       indexes = @Index(name = "idx_pk_email", columnList = "email"))
@Getter
@Setter
@NoArgsConstructor
public class PasskeyCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "credential_id", nullable = false, unique = true, length = 512)
    private String credentialId;  // base64url-encoded

    @Column(name = "public_key_cose", nullable = false, columnDefinition = "TEXT")
    private String publicKeyCose; // base64url-encoded COSE public key

    @Column(name = "sign_count", nullable = false)
    private long signCount = 0;

    @Column(name = "user_handle", nullable = false, length = 512)
    private String userHandle;    // base64url-encoded random bytes, shared across all credentials for a user

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
