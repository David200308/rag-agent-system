package com.agentsystem.conversation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.agentsystem.schema.ChoicePrompt;
import com.agentsystem.schema.ToolActivity;

import java.time.Instant;
import java.util.List;

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

    /** Tool calls behind an assistant answer, for the collapsed activity summary; empty for user turns. */
    @Convert(converter = ToolActivityListConverter.class)
    @Column(name = "tool_activity", columnDefinition = "TEXT")
    private List<ToolActivity> toolActivity = List.of();

    /** Options the assistant offered the user to pick from; null when it asked nothing. */
    @Convert(converter = ChoicePromptConverter.class)
    @Column(name = "choice_prompt", columnDefinition = "TEXT")
    private ChoicePrompt choicePrompt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ConversationMessage(String conversationId, String role, String content, String runId) {
        this.conversationId = conversationId;
        this.role           = role;
        this.content        = content;
        this.runId          = runId;
    }
}
