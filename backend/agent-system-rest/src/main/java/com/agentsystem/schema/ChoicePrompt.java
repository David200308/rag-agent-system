package com.agentsystem.schema;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * A question the assistant asked with a fixed set of answers — rendered as clickable options
 * under the answer. Picking one simply sends it as the user's next message, so no server-side
 * state is kept beyond the message itself.
 */
@Schema(description = "Options the assistant offered the user to pick from")
public record ChoicePrompt(
        @Schema(description = "The question being asked") String question,
        @Schema(description = "The options, in display order") List<String> options,
        @Schema(description = "Whether the user may pick more than one option") boolean multiSelect
) implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public ChoicePrompt {
        options = options == null ? List.of() : List.copyOf(options);
    }

    /**
     * Appends the offered question and options to an assistant turn's text for conversation
     * history, so the model knows what a reply like "Pro" was answering. Mirrors
     * {@code withChoiceNote} in the web frontend's ChoicePromptCard.tsx.
     */
    public static String withHistoryNote(String content, ChoicePrompt prompt) {
        if (prompt == null) return content;
        String note = "[Offered the user options — " + prompt.question() + " "
                + String.join(" | ", prompt.options())
                + (prompt.multiSelect() ? " (multiple allowed)" : "") + "]";
        return content == null || content.isBlank() ? note : content + "\n\n" + note;
    }
}
