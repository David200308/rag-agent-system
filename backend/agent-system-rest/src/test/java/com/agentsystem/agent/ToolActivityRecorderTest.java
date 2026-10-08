package com.agentsystem.agent;

import com.agentsystem.connector.tool.WebFetchAgentTool;
import com.agentsystem.schema.ToolActivity;
import com.agentsystem.schema.ToolActivity.Status;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolActivityRecorderTest {

    private static ToolCallback tool(String name, Function<String, String> body) {
        ToolDefinition def = ToolDefinition.builder().name(name).description(name).inputSchema("{}").build();
        return new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() { return def; }
            @Override public String call(String input) { return body.apply(input); }
        };
    }

    private static ToolCallback wrap(ToolCallback cb, List<ToolActivity> sink) {
        return ToolActivityRecorder.recording(ToolCallbackProvider.from(cb), sink).getToolCallbacks()[0];
    }

    @Test
    void recordsLabelAndDetail_andPassesResultThrough() {
        List<ToolActivity> sink = new ArrayList<>();
        ToolCallback search = wrap(tool("searchWeb", in -> "\"1. Hotel Mozart\""), sink);

        String result = search.call("{\"query\":\"4 star hotel near Spanish Steps\",\"maxResults\":5}");

        assertThat(result).isEqualTo("\"1. Hotel Mozart\"");
        assertThat(sink).containsExactly(new ToolActivity(
                "searchWeb", "Searched the web", "4 star hotel near Spanish Steps", Status.OK));
    }

    @Test
    void fetchWebPage_showsHostOnly() {
        List<ToolActivity> sink = new ArrayList<>();
        wrap(tool("fetchWebPage", in -> "Title: x"), sink)
                .call("{\"url\":\"https://WWW.Example.com/private?token=abc\"}");

        assertThat(sink.get(0).detail()).isEqualTo("www.example.com");
    }

    @Test
    void allowlistRefusal_isBlocked_evenWhenJsonEncoded() {
        List<ToolActivity> sink = new ArrayList<>();
        String encoded = "\"" + WebFetchAgentTool.NOT_ALLOWED_MESSAGE.replace("\"", "\\\"") + "\"";
        wrap(tool("fetchWebPage", in -> encoded), sink).call("{\"url\":\"https://guess.example/\"}");

        assertThat(sink.get(0).status()).isEqualTo(Status.BLOCKED);
    }

    @Test
    void statusOf_classifiesToolMessages() {
        assertThat(ToolActivityRecorder.statusOf("Blocked: Google Web Risk flags x")).isEqualTo(Status.BLOCKED);
        assertThat(ToolActivityRecorder.statusOf(ToolCallBudget.EXHAUSTED_MESSAGE)).isEqualTo(Status.FAILED);
        assertThat(ToolActivityRecorder.statusOf("Could not read Google Doc: 403")).isEqualTo(Status.FAILED);
        assertThat(ToolActivityRecorder.statusOf("Document created successfully.")).isEqualTo(Status.OK);
        assertThat(ToolActivityRecorder.statusOf(null)).isEqualTo(Status.OK);
    }

    @Test
    void thrownException_isRecordedAsFailed_andRethrown() {
        List<ToolActivity> sink = new ArrayList<>();
        ToolCallback broken = wrap(tool("getTravelRecords", in -> { throw new IllegalStateException("boom"); }), sink);

        assertThatThrownBy(() -> broken.call("{}")).isInstanceOf(IllegalStateException.class);
        assertThat(sink).containsExactly(new ToolActivity("getTravelRecords", "Looked up trips", null, Status.FAILED));
    }

    @Test
    void longDetail_isTruncated_andUnknownToolUsesItsName() {
        String longCommand = "x".repeat(500);
        ToolActivity sandbox = ToolActivityRecorder.describe("execInSandbox",
                "{\"command\":\"" + longCommand + "\"}", Status.OK);
        ToolActivity unknown = ToolActivityRecorder.describe("someNewTool", "not json", Status.OK);

        assertThat(sandbox.detail()).hasSize(201).endsWith("…");
        assertThat(unknown).isEqualTo(new ToolActivity("someNewTool", "someNewTool", null, Status.OK));
    }
}
