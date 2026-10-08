package com.agentsystem.webfetch.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A URL that searchWeb returned in some turn of a conversation, so fetchWebPage may open it
 * in later turns too. Keyed by the SHA-256 of the normalized URL — the raw URL is not stored.
 */
@Entity
@Table(name = "conversation_fetch_urls")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationFetchUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", length = 36)
    private String conversationId;

    @Column(name = "url_hash", length = 64)
    private String urlHash;

    @Column(name = "created_at")
    private Instant createdAt;
}
