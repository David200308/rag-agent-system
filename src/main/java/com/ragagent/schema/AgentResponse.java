package com.ragagent.schema;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Pydantic-equivalent structured output schema returned to callers.
 */
@Schema(description = "Response from the RAG agent pipeline")
@JsonClassDescription("Structured response produced by the RAG agent")
public record AgentResponse(

        @Schema(description = "Generated answer")
        @JsonPropertyDescription("The agent's answer to the user's query")
        String answer,

        @Schema(description = "Source documents used to generate the answer")
        List<SourceDocument> sources,

        @Schema(description = "How the agent routed the request")
        RouteDecision routeDecision,

        @Schema(description = "Whether a fallback strategy was activated")
        boolean fallbackActivated,

        @Schema(description = "Reason for fallback activation, if any")
        String fallbackReason,

        @Schema(description = "Metadata about this agent run")
        RunMetadata metadata

) implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    @Schema(description = "A source document retrieved from the vector store")
    @JsonClassDescription("Source document retrieved during RAG")
    public record SourceDocument(
            @JsonPropertyDescription("Unique document identifier") String id,
            @JsonPropertyDescription("Relevant text excerpt")      String content,
            @JsonPropertyDescription("Origin file or URL")         String source,
            @JsonPropertyDescription("Cosine-similarity score")    double score,
            @JsonPropertyDescription("Optional document category") String category
    ) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    @Schema(description = "Routing decision made by the query-analyzer node")
    public record RouteDecision(
            String route,      // RETRIEVE | DIRECT | FALLBACK
            String reasoning,
            double confidence
    ) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    @Schema(description = "Runtime metadata for observability")
    public record RunMetadata(
            String  runId,
            Instant startedAt,
            long    durationMs,
            int     documentsRetrieved,
            String  modelUsed,
            @Schema(description = "Conversation ID — use this in subsequent requests to continue the session")
            String  conversationId
    ) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }
}
