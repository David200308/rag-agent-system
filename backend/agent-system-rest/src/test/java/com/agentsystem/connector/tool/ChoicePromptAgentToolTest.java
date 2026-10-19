package com.agentsystem.connector.tool;

import com.agentsystem.schema.ChoicePrompt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ChoicePromptAgentToolTest {

    final ChoicePromptAgentTool tool = new ChoicePromptAgentTool();
    final AtomicReference<ChoicePrompt> sink = new AtomicReference<>();

    @BeforeEach
    void setUp() { tool.setCurrentSink(sink); }

    @AfterEach
    void tearDown() { tool.clearCurrentSink(); }

    @Test
    void validCall_storesTrimmedDistinctOptions() {
        String result = tool.askUserChoice("  Which plan? ", Arrays.asList(" Basic ", "Pro", "Pro", " ", null), true);

        assertThat(result).doesNotStartWith("Error:");
        assertThat(sink.get().question()).isEqualTo("Which plan?");
        assertThat(sink.get().options()).containsExactly("Basic", "Pro");
        assertThat(sink.get().multiSelect()).isTrue();
    }

    @Test
    void multiSelectOmitted_defaultsToSingle() {
        tool.askUserChoice("Which plan?", List.of("Basic", "Pro"), null);

        assertThat(sink.get().multiSelect()).isFalse();
    }

    @Test
    void tooFewOptions_rejected() {
        assertThat(tool.askUserChoice("Which?", List.of("Only", "Only"), false)).startsWith("Error:");
        assertThat(sink.get()).isNull();
    }

    @Test
    void tooManyOptions_rejected() {
        assertThat(tool.askUserChoice("Which?", List.of("a", "b", "c", "d", "e", "f", "g"), false))
                .startsWith("Error:");
        assertThat(sink.get()).isNull();
    }

    @Test
    void blankQuestion_rejected() {
        assertThat(tool.askUserChoice(" ", List.of("a", "b"), false)).startsWith("Error:");
    }

    @Test
    void secondCallInSameReply_rejectedAndFirstKept() {
        tool.askUserChoice("First?", List.of("a", "b"), false);

        assertThat(tool.askUserChoice("Second?", List.of("c", "d"), false)).startsWith("Error:");
        assertThat(sink.get().question()).isEqualTo("First?");
    }

    @Test
    void noSink_rejected() {
        tool.clearCurrentSink();

        assertThat(tool.askUserChoice("Which?", List.of("a", "b"), false)).startsWith("Error:");
    }
}
