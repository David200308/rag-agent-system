package com.agentsystem.agent.nodes;

import com.agentsystem.agent.state.AgentState;
import com.agentsystem.config.WebFetchProperties;
import com.agentsystem.org.OrgContext;
import com.agentsystem.schema.AgentRequest;
import com.agentsystem.schema.DocumentResult;
import com.agentsystem.webfetch.service.WebFetchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebFetchNodeTest {

    private static final OrgContext NO_USER_CTX = new OrgContext(null, null, "PERSONAL", null);
    private static final OrgContext USER_CTX    = new OrgContext("user-uuid-1", null, "PERSONAL", null);

    @Mock WebFetchService webFetchService;

    // WebFetchProperties is a record — instantiate directly
    WebFetchProperties propsEnabled  = new WebFetchProperties(true, 10, 50_000);
    WebFetchProperties propsDisabled = new WebFetchProperties(false, 10, 50_000);

    WebFetchNode nodeEnabled;
    WebFetchNode nodeDisabled;

    @BeforeEach
    void setUp() {
        nodeEnabled  = new WebFetchNode(webFetchService, propsEnabled);
        nodeDisabled = new WebFetchNode(webFetchService, propsDisabled);
    }

    private static AgentRequest request(String query, List<String> fetchUrls,
                                        boolean webFetch, boolean kb) {
        return new AgentRequest(query, null, null, null, false, null,
                fetchUrls, kb, webFetch);
    }

    private static DocumentResult doc(String url) {
        return new DocumentResult("id-1", "content", 0.9, url, Map.of());
    }

    // ── web fetch globally disabled ────────────────────────────────────────────

    @Test
    void process_globallyDisabled_noFetchAttempted() {
        AgentState state = new AgentState(Map.of(
                "request", request("hello", null, true, true)
        ));

        Map<String, Object> result = nodeDisabled.process(state);

        assertThat(result).isEmpty();
        verifyNoInteractions(webFetchService);
    }

    // ── web fetch disabled per-request ─────────────────────────────────────────

    @Test
    void process_perRequestDisabled_neverCallsFetch() {
        // resolveUrls() still calls isUrlAllowed() internally, but fetch() must not be called
        AgentState state = new AgentState(Map.of(
                "request", request("hello", List.of("https://example.com"), false, true)
        ));

        Map<String, Object> result = nodeEnabled.process(state);

        assertThat(result).doesNotContainKey("documents");
        verify(webFetchService, never()).fetch(anyString(), any(OrgContext.class));
    }

    // ── no URLs ────────────────────────────────────────────────────────────────

    @Test
    void process_noUrls_noFetchAttempted() {
        AgentState state = new AgentState(Map.of(
                "request", request("what is the weather?", null, true, true)
        ));

        Map<String, Object> result = nodeEnabled.process(state);

        assertThat(result).isEmpty();
        verifyNoInteractions(webFetchService);
    }

    // ── explicit fetchUrls allowed ─────────────────────────────────────────────

    @Test
    void process_allowedExplicitUrl_fetchesAndAddsDocuments() {
        when(webFetchService.isUrlAllowed("https://example.com", NO_USER_CTX)).thenReturn(true);
        when(webFetchService.fetch("https://example.com", NO_USER_CTX)).thenReturn(doc("https://example.com"));

        AgentState state = new AgentState(Map.of(
                "request", request("summarise this", List.of("https://example.com"), true, true)
        ));

        Map<String, Object> result = nodeEnabled.process(state);

        @SuppressWarnings("unchecked")
        List<DocumentResult> docs = (List<DocumentResult>) result.get("documents");
        assertThat(docs).hasSize(1);
    }

    // ── URL auto-extracted from query ──────────────────────────────────────────

    @Test
    void process_urlInQueryText_extractsAndFetches() {
        when(webFetchService.isUrlAllowed("https://blog.example.com/post", NO_USER_CTX)).thenReturn(true);
        when(webFetchService.fetch("https://blog.example.com/post", NO_USER_CTX)).thenReturn(doc("https://blog.example.com/post"));

        AgentState state = new AgentState(Map.of(
                "request", request("summarise https://blog.example.com/post for me",
                        null, true, true)
        ));

        Map<String, Object> result = nodeEnabled.process(state);

        @SuppressWarnings("unchecked")
        List<DocumentResult> docs = (List<DocumentResult>) result.get("documents");
        assertThat(docs).hasSize(1);
    }

    // ── Google Workspace URLs skipped ──────────────────────────────────────────

    @Test
    void process_googleDocsUrl_skipped() {
        AgentState state = new AgentState(Map.of(
                "request", request("check this",
                        List.of("https://docs.google.com/document/d/abc123/edit"), true, true)
        ));

        Map<String, Object> result = nodeEnabled.process(state);

        assertThat(result).doesNotContainKey("documents");
        verify(webFetchService, never()).fetch(anyString(), any(OrgContext.class));
    }

    @Test
    void process_googleSheetsUrl_skipped() {
        AgentState state = new AgentState(Map.of(
                "request", request("check this",
                        List.of("https://sheets.google.com/spreadsheets/d/abc/edit"), true, true)
        ));

        Map<String, Object> result = nodeEnabled.process(state);

        assertThat(result).doesNotContainKey("documents");
        verify(webFetchService, never()).fetch(anyString(), any(OrgContext.class));
    }

    // ── URL not in whitelist ───────────────────────────────────────────────────

    @Test
    void process_domainNotAllowed_urlSkipped() {
        when(webFetchService.isUrlAllowed("https://blocked.com", NO_USER_CTX)).thenReturn(false);

        AgentState state = new AgentState(Map.of(
                "request", request("summarise", List.of("https://blocked.com"), true, true)
        ));

        Map<String, Object> result = nodeEnabled.process(state);

        assertThat(result).doesNotContainKey("documents");
        verify(webFetchService, never()).fetch(anyString(), any(OrgContext.class));
    }

    // ── fetch throws exception → URL skipped ──────────────────────────────────

    @Test
    void process_fetchThrowsException_urlSkippedAndOthersStillFetched() {
        when(webFetchService.isUrlAllowed(anyString(), any(OrgContext.class))).thenReturn(true);
        when(webFetchService.fetch("https://failing.com", NO_USER_CTX))
                .thenThrow(new RuntimeException("connection refused"));
        when(webFetchService.fetch("https://ok.com", NO_USER_CTX)).thenReturn(doc("https://ok.com"));

        AgentState state = new AgentState(Map.of(
                "request", request("summarise",
                        List.of("https://failing.com", "https://ok.com"), true, true)
        ));

        Map<String, Object> result = nodeEnabled.process(state);

        @SuppressWarnings("unchecked")
        List<DocumentResult> docs = (List<DocumentResult>) result.get("documents");
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).source()).isEqualTo("https://ok.com");
    }

    // ── FALLBACK route promoted to RETRIEVE when docs fetched ─────────────────

    @Test
    void process_fallbackRouteWithFetchedDocs_promotedToRetrieve() {
        when(webFetchService.isUrlAllowed("https://example.com", NO_USER_CTX)).thenReturn(true);
        when(webFetchService.fetch("https://example.com", NO_USER_CTX)).thenReturn(doc("https://example.com"));

        AgentState state = new AgentState(Map.of(
                "request", request("summarise this", List.of("https://example.com"), true, true),
                "route",   "FALLBACK"
        ));

        Map<String, Object> result = nodeEnabled.process(state);

        assertThat(result.get("route")).isEqualTo("RETRIEVE");
    }

    @Test
    void process_retrieveRouteWithFetchedDocs_routeNotChanged() {
        when(webFetchService.isUrlAllowed("https://example.com", NO_USER_CTX)).thenReturn(true);
        when(webFetchService.fetch("https://example.com", NO_USER_CTX)).thenReturn(doc("https://example.com"));

        // Default route is RETRIEVE
        AgentState state = new AgentState(Map.of(
                "request", request("summarise this", List.of("https://example.com"), true, true)
        ));

        Map<String, Object> result = nodeEnabled.process(state);

        assertThat(result).doesNotContainEntry("route", "RETRIEVE"); // not explicitly set again
    }

    // ── KB disabled forces DIRECT ──────────────────────────────────────────────

    @Test
    void process_kbDisabled_routeForcedToDirect() {
        AgentState state = new AgentState(Map.of(
                "request", request("what is the capital?", null, true, false)
        ));

        Map<String, Object> result = nodeEnabled.process(state);

        assertThat(result.get("route")).isEqualTo("DIRECT");
    }

    @Test
    void process_kbDisabledWithFetchedDocs_routeForcedToDirect() {
        when(webFetchService.isUrlAllowed("https://example.com", NO_USER_CTX)).thenReturn(true);
        when(webFetchService.fetch("https://example.com", NO_USER_CTX)).thenReturn(doc("https://example.com"));

        AgentState state = new AgentState(Map.of(
                "request", request("summarise this", List.of("https://example.com"), true, false)
        ));

        Map<String, Object> result = nodeEnabled.process(state);

        // docs fetched → route promoted to RETRIEVE by fetch, but then KB disabled overrides to DIRECT
        assertThat(result.get("route")).isEqualTo("DIRECT");
    }

    // ── userUuid propagated to fetch service ────────────────────────────────────

    @Test
    void process_withUserUuid_passesUuidToFetchService() {
        when(webFetchService.isUrlAllowed("https://example.com", USER_CTX)).thenReturn(true);
        when(webFetchService.fetch("https://example.com", USER_CTX)).thenReturn(doc("https://example.com"));

        AgentState state = new AgentState(Map.of(
                "request",  request("summarise this", List.of("https://example.com"), true, true),
                "userUuid", "user-uuid-1"
        ));

        Map<String, Object> result = nodeEnabled.process(state);

        verify(webFetchService).isUrlAllowed("https://example.com", USER_CTX);
        verify(webFetchService).fetch("https://example.com", USER_CTX);
    }
}
