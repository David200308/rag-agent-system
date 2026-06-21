package com.ragagent.conversation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "conversation_messages")
@Getter
@Setter
@NoArgsConstructor
public class ConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    /** "user" or "assistant" */
    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Links back to AgentResponse.RunMetadata.runId — nullable for user turns. */
    @Column(name = "run_id", length = 36)
    private String runId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ConversationMessage(String conversationId, String role, String content, String runId) {
        this.conversationId = conversationId;
        this.role           = role;
        this.content        = content;
        this.runId          = runId;
    }
}
