package com.ragagent.agent.state;

import com.ragagent.schema.AgentRequest;
import com.ragagent.schema.AgentResponse;
import com.ragagent.schema.DocumentResult;
import com.ragagent.schema.QueryAnalysis;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Typed state object shared across all LangGraph4j nodes.
 *
 * Each field is a named Channel that controls how concurrent or sequential
 * updates are merged. LastValue channels overwrite; Appender channels accumulate.
 *
 * Extending {@code org.bsc.langgraph4j.state.AgentState} gives access to the
 * {@code value(key)} accessor and the snapshot/restore machinery used by the
 * compiled graph.
 */
public class AgentState extends org.bsc.langgraph4j.state.AgentState {

    /** Channel definitions — passed to StateGraph as the schema. */
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
        "request",        Channels.<AgentRequest>base((cur, upd) -> upd),
        "queryAnalysis",  Channels.<QueryAnalysis>base((cur, upd) -> upd),
        "documents",      Channels.appender(java.util.ArrayList::new),
        "response",       Channels.<AgentResponse>base((cur, upd) -> upd),
        "route",          Channels.base(() -> QueryAnalysis.Route.RETRIEVE.name()),
        "error",          Channels.<String>base((cur, upd) -> upd),
        "fallbackReason", Channels.<String>base((cur, upd) -> upd),
        "runId",          Channels.<String>base((cur, upd) -> upd),
        "userEmail",      Channels.<String>base((cur, upd) -> upd)
    );

    public AgentState(Map<String, Object> initData) {
        super(initData);
    }

    // ── Typed accessors ──────────────────────────────────────────────────────

    public Optional<AgentRequest> request() {
        return value("request");
    }

    public Optional<QueryAnalysis> queryAnalysis() {
        return value("queryAnalysis");
    }

    @SuppressWarnings("unchecked")
    public List<DocumentResult> documents() {
        return (List<DocumentResult>) value("documents").orElse(List.of());
    }

    public Optional<AgentResponse> response() {
        return value("response");
    }

    public String route() {
        return (String) value("route").orElse(QueryAnalysis.Route.RETRIEVE.name());
    }

    public Optional<String> error() {
        return value("error");
    }

    public Optional<String> fallbackReason() {
        return value("fallbackReason");
    }

    public Optional<String> runId() {
        return value("runId");
    }

    public Optional<String> userEmail() {
        return value("userEmail");
    }
}
