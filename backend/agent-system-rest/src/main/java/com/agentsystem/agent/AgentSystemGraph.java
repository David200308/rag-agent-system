package com.agentsystem.agent;

import com.agentsystem.agent.nodes.FallbackNode;
import com.agentsystem.agent.nodes.GeneratorNode;
import com.agentsystem.agent.nodes.QueryAnalyzerNode;
import com.agentsystem.agent.nodes.RetrievalNode;
import com.agentsystem.agent.nodes.WebFetchNode;
import com.agentsystem.agent.state.AgentState;
import com.agentsystem.schema.QueryAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * LangGraph4j agent graph wiring.
 *
 * Flow:
 * <pre>
 *   START
 *     └─► analyzeQuery
 *               └─► webFetch  (no-op when fetchUrls is empty)
 *                       ├─[RETRIEVE]─► retrieve ─[docs found]──► generate
 *                       │                        └[no docs]───► fallback ─► END
 *                       ├─[DIRECT]───────────────────────────► generate
 *                       └─[FALLBACK]──────────────────────────► fallback ─► END
 *
 *   generate ─[succeeded]─► END
 *            └[LLM circuit open / retries exhausted]─► fallback ─► END
 * </pre>
 *
 * Virtual threads (JDK 21) execute each async node action via Spring's
 * {@code spring.threads.virtual.enabled=true} setting.
 */
@Slf4j
@Component
public class AgentSystemGraph {

    private static final String NODE_ANALYZE    = "analyzeQuery";
    private static final String NODE_WEB_FETCH  = "webFetch";
    private static final String NODE_RETRIEVE   = "retrieve";
    private static final String NODE_GENERATE   = "generate";
    private static final String NODE_FALLBACK   = "fallback";

    private final CompiledGraph<AgentState> compiledGraph;

    public AgentSystemGraph(QueryAnalyzerNode analyzerNode,
                         WebFetchNode      webFetchNode,
                         RetrievalNode     retrievalNode,
                         GeneratorNode     generatorNode,
                         FallbackNode      fallbackNode) throws Exception {

        compiledGraph = new StateGraph<>(AgentState.SCHEMA, AgentState::new)

            // ── Nodes ──────────────────────────────────────────────────────
            .addNode(NODE_ANALYZE,   node_async(analyzerNode::process))
            .addNode(NODE_WEB_FETCH, node_async(webFetchNode::process))
            .addNode(NODE_RETRIEVE,  node_async(retrievalNode::process))
            .addNode(NODE_GENERATE,  node_async(generatorNode::process))
            .addNode(NODE_FALLBACK,  node_async(fallbackNode::process))

            // ── Edges ──────────────────────────────────────────────────────
            .addEdge(START, NODE_ANALYZE)

            // After analysis: always pass through webFetch (no-op when fetchUrls empty)
            .addEdge(NODE_ANALYZE, NODE_WEB_FETCH)

            // After webFetch: route based on QueryAnalysis.Route (may be updated by webFetch)
            .addConditionalEdges(
                NODE_WEB_FETCH,
                state -> CompletableFuture.completedFuture(state.route()),
                java.util.Map.of(
                    QueryAnalysis.Route.RETRIEVE.name(), NODE_RETRIEVE,
                    QueryAnalysis.Route.DIRECT.name(),   NODE_GENERATE,
                    QueryAnalysis.Route.FALLBACK.name(), NODE_FALLBACK
                )
            )

            // After retrieval: check if documents were found
            .addConditionalEdges(
                NODE_RETRIEVE,
                state -> CompletableFuture.completedFuture(state.documents().isEmpty() ? "noDocuments" : "found"),
                java.util.Map.of(
                    "found",       NODE_GENERATE,
                    "noDocuments", NODE_FALLBACK
                )
            )

            // After generation: an LLM circuit-breaker/retry exhaustion (GenerationService)
            // leaves state.response() empty so GeneratorNode can route to fallback instead
            // of surfacing a raw failure.
            .addConditionalEdges(
                NODE_GENERATE,
                state -> CompletableFuture.completedFuture(state.response().isEmpty() ? "failed" : "succeeded"),
                java.util.Map.of(
                    "succeeded", END,
                    "failed",    NODE_FALLBACK
                )
            )

            .addEdge(NODE_FALLBACK, END)

            .compile();

        log.info("[AgentSystemGraph] Compiled graph ready");
    }

    public CompiledGraph<AgentState> getGraph() {
        return compiledGraph;
    }
}
