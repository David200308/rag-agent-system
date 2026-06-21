package com.ragagent.agent.nodes;

import com.ragagent.agent.state.AgentState;
import com.ragagent.fallback.FallbackService;
import com.ragagent.schema.AgentRequest;
import com.ragagent.schema.AgentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FallbackNodeTest {

    @Mock FallbackService fallbackService;
    @InjectMocks FallbackNode fallbackNode;

    private static AgentRequest request(String query) {
        return new AgentRequest(query, null, null, null, false, null, null, null, null);
    }

    @Test
    void process_returnsResponseWithFallbackActivatedTrue() {
        AgentState state = new AgentState(Map.of(
                "request",        request("what is 2+2?"),
                "fallbackReason", "out-of-scope",
                "runId",          "run-abc"
        ));
        when(fallbackService.resolveFallback(eq("what is 2+2?"), eq("out-of-scope"), any(Optional.class)))
                .thenReturn("I cannot answer this.");

        Map<String, Object> result = fallbackNode.process(state);

        AgentResponse response = (AgentResponse) result.get("response");
        assertThat(response).isNotNull();
        assertThat(response.fallbackActivated()).isTrue();
        assertThat(response.answer()).isEqualTo("I cannot answer this.");
        assertThat(response.fallbackReason()).isEqualTo("out-of-scope");
    }

    @Test
    void process_storesRunIdFromState() {
        AgentState state = new AgentState(Map.of(
                "request", request("query"),
                "runId",   "my-run-id-123"
        ));
        when(fallbackService.resolveFallback(any(), any(), any())).thenReturn("Sorry");

        Map<String, Object> result = fallbackNode.process(state);

        AgentResponse response = (AgentResponse) result.get("response");
        assertThat(response.metadata().runId()).isEqualTo("my-run-id-123");
    }

    @Test
    void process_noRunId_generatesRandomRunId() {
        AgentState state = new AgentState(Map.of("request", request("query")));
        when(fallbackService.resolveFallback(any(), any(), any())).thenReturn("Sorry");

        Map<String, Object> result = fallbackNode.process(state);

        AgentResponse response = (AgentResponse) result.get("response");
        assertThat(response.metadata().runId()).isNotNull().isNotEmpty();
    }

    @Test
    void process_routeDecisionIsFallback() {
        AgentState state = new AgentState(Map.of("request", request("query")));
        when(fallbackService.resolveFallback(any(), any(), any())).thenReturn("Sorry");

        Map<String, Object> result = fallbackNode.process(state);

        AgentResponse response = (AgentResponse) result.get("response");
        assertThat(response.routeDecision().route()).isEqualTo("FALLBACK");
    }

    @Test
    void process_noSources() {
        AgentState state = new AgentState(Map.of("request", request("query")));
        when(fallbackService.resolveFallback(any(), any(), any())).thenReturn("No sources");

        Map<String, Object> result = fallbackNode.process(state);

        AgentResponse response = (AgentResponse) result.get("response");
        assertThat(response.sources()).isEmpty();
    }
}
