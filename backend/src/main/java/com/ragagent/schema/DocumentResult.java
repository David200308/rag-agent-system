package com.ragagent.schema;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * Pydantic-equivalent schema representing a single retrieved document chunk.
 */
@Schema(description = "A retrieved document chunk from Weaviate")
@JsonClassDescription("Document chunk retrieved from the vector store")
public record DocumentResult(

        @Schema(description = "Weaviate object UUID") String id,
        @Schema(description = "Text content of the chunk") String content,
        @Schema(description = "Cosine similarity score") double score,
        @Schema(description = "Document source path/URL") String source,
        @Schema(description = "Arbitrary metadata attached to the chunk") Map<String, Object> metadata

) implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** Returns true when the score meets the minimum similarity threshold. */
    public boolean isRelevant(double threshold) {
        return score >= threshold;
    }
}
