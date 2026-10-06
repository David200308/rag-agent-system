package com.agentsystem.webrisk.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Durable (L3) tier of the Web Risk verdict cache. Keyed by the SHA-256 of the
 * normalized URL — the raw URL is not stored, since user-supplied URLs can carry
 * tokens or personal data in their query strings.
 */
@Entity
@Table(name = "web_risk_url_verdicts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebRiskUrlVerdict {

    @Id
    @Column(name = "url_hash", length = 64)
    private String urlHash;

    @Column(length = 255)
    private String host;

    private boolean safe;

    /** Comma-separated Web Risk threat types; empty when safe. */
    @Column(name = "threat_types", length = 255)
    private String threatTypes;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "checked_at")
    private Instant checkedAt;
}
