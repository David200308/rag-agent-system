package com.ragagent.conversation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "conversation_shares",
       uniqueConstraints = @UniqueConstraint(columnNames = "token"))
@Getter
@Setter
@NoArgsConstructor
public class ConversationShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    /** Opaque URL-safe token used in the share link. */
    @Column(nullable = false, length = 36)
    private String token;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    /** Null means the link never expires. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ConversationShare(String conversationId, String token,
                             String ownerEmail, Instant expiresAt) {
        this.conversationId = conversationId;
        this.token          = token;
        this.ownerEmail     = ownerEmail;
        this.expiresAt      = expiresAt;
    }

    /** Returns true if the link is still valid (not expired). */
    public boolean isActive() {
        return expiresAt == null || Instant.now().isBefore(expiresAt);
    }
}
