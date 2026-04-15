package com.ragagent.agent.nodes;

import com.ragagent.agent.state.AgentState;
import com.ragagent.config.WebFetchProperties;
import com.ragagent.schema.AgentRequest;
import com.ragagent.schema.DocumentResult;
import com.ragagent.webfetch.WebFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Node 1.5 — Web Fetch.
 *
 * Runs between analyzeQuery and the route decision. Applies two per-request toggles:
 *
 * <ul>
 *   <li><b>useWebFetch</b> — if false (or global {@code web-fetch.enabled=false}),
 *       URL fetching is skipped even when fetchUrls are provided.</li>
 *   <li><b>useKnowledgeBase</b> — if false, the route is forced to DIRECT so
 *       {@link RetrievalNode} is bypassed entirely. If URLs were also fetched,
 *       those docs are still available to the Generator via state.</li>
 * </ul>
 *
 * When at least one URL is successfully fetched and the current route is FALLBACK,
 * the route is promoted to RETRIEVE so the Generator is reached.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebFetchNode {

    private final WebFetchService     webFetchService;
    private final WebFetchProperties  props;

    public Map<String, Object> process(AgentState state) {
        AgentRequest request = state.request().orElseThrow();

        Map<String, Object> updates = new java.util.HashMap<>();

        // ── Web fetch ──────────────────────────────────────────────────────────
        boolean webFetchEnabled = props.enabled() && request.isWebFetchEnabled();
        List<String> urls = request.fetchUrls();

        if (webFetchEnabled && urls != null && !urls.isEmpty()) {
            List<DocumentResult> fetched = new ArrayList<>();
            for (String url : urls) {
                try {
                    fetched.add(webFetchService.fetch(url));
                } catch (Exception e) {
                    log.warn("[WebFetchNode] Skipping URL '{}': {}", url, e.getMessage());
                }
            }
            if (!fetched.isEmpty()) {
                log.info("[WebFetchNode] Fetched {} URL(s)", fetched.size());
                updates.put("documents", fetched);
                // Promote FALLBACK → RETRIEVE so the Generator is reached
                if ("FALLBACK".equals(state.route())) {
                    updates.put("route", "RETRIEVE");
                }
            }
        }

        // ── Knowledge base toggle ──────────────────────────────────────────────
        // If KB search is disabled, force DIRECT so RetrievalNode is bypassed.
        // Fetched URL docs (if any) are already in state and will reach the Generator.
        if (!request.isKnowledgeBaseEnabled()) {
            String currentRoute = (String) updates.getOrDefault("route", state.route());
            if ("RETRIEVE".equals(currentRoute)) {
                log.info("[WebFetchNode] Knowledge base disabled — routing DIRECT");
                updates.put("route", "DIRECT");
            }
        }

        return updates;
    }
}
