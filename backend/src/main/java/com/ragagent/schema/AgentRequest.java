package com.ragagent.schema;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Pydantic-equivalent input schema for the RAG agent.
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
@Schema(description = "Request payload for the RAG agent")
@JsonClassDescription("Input for the RAG agent pipeline")
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

        @Schema(description = "Optional list of URLs to fetch and include as context. "
                + "Each URL must match a domain in the web-fetch whitelist.")
        @jakarta.validation.constraints.Size(max = 5, message = "At most 5 URLs may be fetched per request")
        List<String> fetchUrls,

        @Schema(description = "Whether to search the knowledge base for this request. "
                + "Defaults to true. Set false to answer from LLM knowledge (or fetched URLs) only.")
        Boolean useKnowledgeBase,

        @Schema(description = "Whether to fetch the provided URLs for this request. "
                + "Defaults to true. Set false to ignore fetchUrls even if supplied.")
        Boolean useWebFetch

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

    /** Returns true unless explicitly disabled. */
    public boolean isWebFetchEnabled() {
        return useWebFetch == null || useWebFetch;
    }

    @Schema(description = "A single turn in the conversation history")
    public record ConversationTurn(
            @NotBlank String role,   // "user" | "assistant"
            @NotBlank String content
    ) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }
}
