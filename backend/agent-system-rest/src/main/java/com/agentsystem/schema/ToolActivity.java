package com.agentsystem.schema;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

/**
 * One tool call the assistant made while answering — shown to the user as a collapsed
 * "Searched the web, read 2 pages" summary above the answer. Holds only a short label and
 * detail (search query, page host, document title), never the tool's raw output.
 */
@Schema(description = "A tool call made while generating the answer")
public record ToolActivity(
        @Schema(description = "Tool method name, e.g. searchWeb") String tool,
        @Schema(description = "Human-readable action, e.g. 'Searched the web'") String label,
        @Schema(description = "Short detail, e.g. the search query or page host; may be null") String detail,
        @Schema(description = "OK | FAILED | BLOCKED") Status status
) implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public enum Status { OK, FAILED, BLOCKED }
}
