package com.ragagent.schema;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Pydantic-equivalent schema for the LLM-powered query-analysis step.
 *
 * Spring AI's {@code BeanOutputConverter<QueryAnalysis>} will instruct the LLM
 * to produce valid JSON that maps to this record, then deserialise and validate
 * it—exactly as Pydantic does for structured LLM outputs.
 */
@Schema(description = "Structured analysis of the user's query produced by the LLM")
@JsonClassDescription("Analysis of a user query to guide routing and retrieval")
public record QueryAnalysis(

        @Schema(description = "Cleaned, disambiguated version of the original query")
        @JsonPropertyDescription("Rephrased query optimised for vector-store retrieval")
        @NotBlank
        String refinedQuery,

        @Schema(description = "Routing decision: RETRIEVE | DIRECT | FALLBACK")
        @JsonPropertyDescription("Determines which graph node handles the request next")
        @NotNull
        Route route,

        @Schema(description = "Confidence in the routing decision, 0.0–1.0")
        @JsonPropertyDescription("Routing confidence score")
        @DecimalMin("0.0") @DecimalMax("1.0")
        double routeConfidence,

        @Schema(description = "Key topics or entities extracted from the query")
        @JsonPropertyDescription("Named entities and keywords relevant to retrieval")
        List<String> keywords,

        @Schema(description = "Sub-questions decomposed from complex queries")
        @JsonPropertyDescription("Optional list of sub-questions for multi-hop retrieval")
        List<String> subQuestions,

        @Schema(description = "One-sentence explanation of the routing decision")
        @JsonPropertyDescription("Reasoning behind the chosen route")
        String reasoning

) implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public enum Route {
        /** Perform vector-store retrieval before generating an answer. */
        RETRIEVE,
        /** Answer directly from LLM knowledge without retrieval. */
        DIRECT,
        /** Activate fallback — query is out-of-scope or ambiguous. */
        FALLBACK
    }
}
