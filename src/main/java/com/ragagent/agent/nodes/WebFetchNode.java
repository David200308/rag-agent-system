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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Node 1.5 — Web Fetch.
 *
 * Runs between analyzeQuery and the route decision. Applies two per-request toggles:
 *
 * <ul>
 *   <li><b>useWebFetch</b> — if false (or global {@code web-fetch.enabled=false}),
 *       URL fetching is skipped entirely.</li>
 *   <li><b>useKnowledgeBase</b> — if false, the route is forced to DIRECT so
 *       {@link RetrievalNode} is bypassed entirely. If URLs were also fetched,
 *       those docs are still available to the Generator via state.</li>
 * </ul>
 *
 * URL resolution order (all on the backend):
 * <ol>
 *   <li>Explicit {@code fetchUrls} provided by the caller.</li>
 *   <li>URLs auto-extracted from the query text.</li>
 * </ol>
 * Duplicates are removed; at most 5 URLs are fetched (backend limit mirrors frontend).
 *
 * When at least one URL is successfully fetched and the current route is FALLBACK,
 * the route is promoted to RETRIEVE so the Generator is reached.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebFetchNode {

    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[^\\s\"'<>]+");
    private static final Pattern GOOGLE_WORKSPACE_PATTERN =
            Pattern.compile("https?://(?:docs|sheets|slides)\\.google\\.com/");
    private static final int MAX_URLS = 5;

    private final WebFetchService     webFetchService;
    private final WebFetchProperties  props;

    public Map<String, Object> process(AgentState state) {
        AgentRequest request = state.request().orElseThrow();
        String userEmail = state.userEmail().orElse(null);

        Map<String, Object> updates = new java.util.HashMap<>();

        // ── Web fetch ──────────────────────────────────────────────────────────
        boolean webFetchEnabled = props.enabled() && request.isWebFetchEnabled();

        // Merge explicit fetchUrls + URLs auto-extracted from the query text
        List<String> urls = resolveUrls(request, userEmail);

        if (webFetchEnabled && !urls.isEmpty()) {
            List<DocumentResult> fetched = new ArrayList<>();
            for (String url : urls) {
                try {
                    fetched.add(webFetchService.fetch(url, userEmail));
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> resolveUrls(AgentRequest request, String userEmail) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        if (request.fetchUrls() != null) {
            request.fetchUrls().forEach(u -> seen.add(u.strip()));
        }

        Matcher m = URL_PATTERN.matcher(request.query());
        while (m.find()) {
            String url = m.group().replaceAll("[.,!?;:)]+$", "");
            seen.add(url);
        }

        List<String> allowed = new ArrayList<>();
        for (String url : seen) {
            if (GOOGLE_WORKSPACE_PATTERN.matcher(url).find()) {
                log.debug("[WebFetchNode] Skipping Google Workspace URL '{}': handled by agent tools", url);
                continue;
            }
            if (webFetchService.isUrlAllowed(url, userEmail)) {
                allowed.add(url);
                if (allowed.size() == MAX_URLS) break;
            } else {
                log.warn("[WebFetchNode] Skipping URL '{}': domain not in user whitelist", url);
            }
        }
        return allowed;
    }
}
