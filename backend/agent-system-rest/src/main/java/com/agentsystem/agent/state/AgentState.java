package com.agentsystem.agent.state;

import com.agentsystem.schema.AgentRequest;
import com.agentsystem.schema.AgentResponse;
import com.agentsystem.schema.DocumentResult;
import com.agentsystem.schema.QueryAnalysis;
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
    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
        Map.entry("request",                  Channels.<AgentRequest>base((cur, upd) -> upd)),
        Map.entry("queryAnalysis",            Channels.<QueryAnalysis>base((cur, upd) -> upd)),
        Map.entry("documents",                Channels.appender(java.util.ArrayList::new)),
        Map.entry("response",                 Channels.<AgentResponse>base((cur, upd) -> upd)),
        Map.entry("route",                    Channels.base(() -> QueryAnalysis.Route.RETRIEVE.name())),
        Map.entry("error",                    Channels.<String>base((cur, upd) -> upd)),
        Map.entry("fallbackReason",           Channels.<String>base((cur, upd) -> upd)),
        Map.entry("runId",                    Channels.<String>base((cur, upd) -> upd)),
        Map.entry("userUuid",                 Channels.<String>base((cur, upd) -> upd)),
        Map.entry("orgId",                    Channels.<String>base((cur, upd) -> upd)),
        Map.entry("shareOwnerEmail",          Channels.<String>base((cur, upd) -> upd)),
        Map.entry("selectedModelDisplayName", Channels.<String>base((cur, upd) -> upd))
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

    public Optional<String> userUuid() {
        return value("userUuid");
    }

    public Optional<String> orgId() {
        return value("orgId");
    }

    public Optional<String> shareOwnerEmail() {
        return value("shareOwnerEmail");
    }

    public Optional<String> selectedModelDisplayName() {
        return value("selectedModelDisplayName");
    }
}
