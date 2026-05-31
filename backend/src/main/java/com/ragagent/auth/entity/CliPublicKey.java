package com.ragagent.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "cli_public_keys")
@Getter
@Setter
@NoArgsConstructor
public class CliPublicKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userEmail;

    // Raw Ed25519 public key (32 bytes), Base64-encoded — 44 chars
    @Column(nullable = false, length = 64)
    private String publicKeyBase64;

    // First 8 chars of Base64 — shown in `auth status` for verification
    @Column(nullable = false, length = 8)
    private String fingerprint;

    @Column(nullable = false, updatable = false)
    private Instant registeredAt = Instant.now();

    private Instant lastSeenAt;

    public CliPublicKey(String userEmail, String publicKeyBase64) {
        this.userEmail      = userEmail;
        this.publicKeyBase64 = publicKeyBase64;
        this.fingerprint    = publicKeyBase64.substring(0, 8);
    }
}
