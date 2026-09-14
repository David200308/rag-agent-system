package com.agentsystem.schema;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pydantic-equivalent input schema for the Agent System.
 *
 * In Java/Spring AI, "Pydantic" is implemented via:
 *   - Bean Validation (JSR-380) annotations  → field constraints
 *   - Jackson annotations                    → JSON serialisation rules
 *   - @Schema (OpenAPI)                      → documentation / schema export
 *   - Spring AI BeanOutputConverter          → LLM → typed object parsing
 *
 * All three layers are applied here so the class is usable both as an HTTP
 * request body and as a structured-output target for an LLM call.
 */
@Schema(description = "Request payload for the Agent System")
@JsonClassDescription("Input for the Agent System pipeline")
public record AgentRequest(

        @Schema(description = "Natural-language query", example = "What are the main risks of transformer models?")
        @JsonPropertyDescription("The user's question or task description")
        @NotBlank(message = "Query must not be blank")
        @Size(min = 3, max = 2000, message = "Query must be between 3 and 2000 characters")
        String query,

        @Schema(description = "Optional filters applied to vector-store retrieval")
        Map<String, String> filters,

        @Schema(description = "Max number of source documents to retrieve (1-20), defaults to 5")
        @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(20)
        Integer topK,

        @Schema(description = "Optional conversation history for multi-turn sessions")
        List<ConversationTurn> conversationHistory,

        @Schema(description = "Whether to stream the response (SSE)", defaultValue = "false")
        boolean stream,

        @Schema(description = "Optional conversation ID for persisted multi-turn sessions. "
                + "Omit to start a new conversation; include to continue an existing one.")
        String conversationId,

        @Schema(description = "Whether to search the knowledge base for this request. "
                + "Defaults to true. Set false to answer from LLM knowledge only.")
        Boolean useKnowledgeBase,

        @Schema(description = "Whether the assistant may use the web-search tool for this request. "
                + "Defaults to false — the caller must opt in.")
        Boolean useWebSearch,

        @Schema(description = "Optional model display name to use for this turn (and persist to the "
                + "conversation going forward). Takes priority over the conversation's/user's stored "
                + "model — lets the caller switch models and have it apply starting with THIS message, "
                + "rather than only from the next turn once a separate 'set model' call lands.")
        String selectedModel

) implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** Default topK when caller omits it. */
    public int effectiveTopK() {
        return topK != null ? topK : 5;
    }

    /** Returns true unless explicitly disabled. */
    public boolean isKnowledgeBaseEnabled() {
        return useKnowledgeBase == null || useKnowledgeBase;
    }

    /** Returns false unless explicitly enabled — the caller must opt in. */
    public boolean isWebSearchEnabled() {
        return Boolean.TRUE.equals(useWebSearch);
    }

    @Schema(description = "A single turn in the conversation history")
    public record ConversationTurn(
            @NotBlank String role,   // "user" | "assistant"
            @NotBlank String content
    ) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }
}
