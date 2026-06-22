package com.agentsystem.agent.nodes;

import com.agentsystem.agent.state.AgentState;
import com.agentsystem.knowledge.service.KnowledgeSourceService;
import com.agentsystem.knowledge.entity.KnowledgeSource;
import com.agentsystem.rag.service.RetrievalService;
import com.agentsystem.schema.AgentRequest;
import com.agentsystem.schema.DocumentResult;
import com.agentsystem.schema.QueryAnalysis;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalNodeTest {

    @Mock RetrievalService       retrievalService;
    @Mock KnowledgeSourceService knowledgeSourceService;
    @InjectMocks RetrievalNode   retrievalNode;

    private static AgentRequest request(boolean kbEnabled) {
        return new AgentRequest("question?", null, 5, null, false, null, null, kbEnabled, null);
    }

    private static QueryAnalysis analysis() {
        return new QueryAnalysis("refined question", QueryAnalysis.Route.RETRIEVE,
                0.9, List.of(), null, "retrieval needed");
    }

    private static KnowledgeSource ks(String source) {
        return new KnowledgeSource(source, source, null, 10, "owner@example.com");
    }

    private static DocumentResult doc(String id) {
        return new DocumentResult(id, "content text", 0.9, "source.pdf", Map.of());
    }

    // ── knowledge base disabled ────────────────────────────────────────────────

    @Test
    void process_knowledgeBaseDisabled_returnsEmptyMap() {
        AgentState state = new AgentState(Map.of(
                "request",       request(false),
                "queryAnalysis", analysis()
        ));

        Map<String, Object> result = retrievalNode.process(state);

        assertThat(result).isEmpty();
    }

    // ── documents found ────────────────────────────────────────────────────────

    @Test
    void process_documentsFound_returnsDocumentsList() {
        when(knowledgeSourceService.listAccessible("user@example.com"))
                .thenReturn(List.of(ks("source.pdf")));
        when(retrievalService.retrieve(anyString(), anyInt(), any(), any()))
                .thenReturn(List.of(doc("id-1"), doc("id-2")));

        AgentState state = new AgentState(Map.of(
                "request",       request(true),
                "queryAnalysis", analysis(),
                "userEmail",     "user@example.com"
        ));

        Map<String, Object> result = retrievalNode.process(state);

        @SuppressWarnings("unchecked")
        List<DocumentResult> docs = (List<DocumentResult>) result.get("documents");
        assertThat(docs).hasSize(2);
        assertThat(docs.get(0).id()).isEqualTo("id-1");
    }

    // ── no documents found ─────────────────────────────────────────────────────

    @Test
    void process_noDocuments_routesToFallbackWithReason() {
        when(knowledgeSourceService.listAccessible("user@example.com")).thenReturn(List.of());
        when(retrievalService.retrieve(anyString(), anyInt(), any(), any())).thenReturn(List.of());

        AgentState state = new AgentState(Map.of(
                "request",       request(true),
                "queryAnalysis", analysis(),
                "userEmail",     "user@example.com"
        ));

        Map<String, Object> result = retrievalNode.process(state);

        assertThat(result.get("route")).isEqualTo("FALLBACK");
        assertThat(result.get("fallbackReason").toString()).contains("No relevant");
    }

    // ── null userEmail (auth disabled) ─────────────────────────────────────────

    @Test
    void process_nullUserEmail_passesNullAllowedSources() {
        DocumentResult doc = doc("id-1");
        when(retrievalService.retrieve(anyString(), anyInt(), any(), isNull()))
                .thenReturn(List.of(doc));

        AgentState state = new AgentState(Map.of(
                "request",       request(true),
                "queryAnalysis", analysis()
                // no userEmail → null → unrestricted
        ));

        Map<String, Object> result = retrievalNode.process(state);

        verify(retrievalService).retrieve(anyString(), anyInt(), any(), isNull());
        @SuppressWarnings("unchecked")
        List<DocumentResult> docs = (List<DocumentResult>) result.get("documents");
        assertThat(docs).hasSize(1);
    }

    // ── allowed source resolution ──────────────────────────────────────────────

    @Test
    void process_userEmailSet_restrictsToAccessibleSources() {
        KnowledgeSource source = ks("my-doc.pdf");
        when(knowledgeSourceService.listAccessible("user@example.com"))
                .thenReturn(List.of(source));
        when(retrievalService.retrieve(anyString(), anyInt(), any(), argThat(set ->
                set != null && set.contains("my-doc.pdf"))))
                .thenReturn(List.of(doc("id-1")));

        AgentState state = new AgentState(Map.of(
                "request",       request(true),
                "queryAnalysis", analysis(),
                "userEmail",     "user@example.com"
        ));

        retrievalNode.process(state);

        verify(knowledgeSourceService).listAccessible("user@example.com");
    }
}
