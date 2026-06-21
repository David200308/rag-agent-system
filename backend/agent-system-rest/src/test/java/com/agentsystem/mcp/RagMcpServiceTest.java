package com.agentsystem.mcp;

import com.agentsystem.config.McpProperties;
import com.agentsystem.knowledge.KnowledgeSourceService;
import com.agentsystem.knowledge.entity.KnowledgeSource;
import com.agentsystem.rag.RetrievalService;
import com.agentsystem.schema.DocumentResult;
import com.agentsystem.schema.UrlIngestionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagMcpServiceTest {

    @Mock RetrievalService       retrievalService;
    @Mock McpConnectorService    mcpConnectorService;
    @Mock KnowledgeSourceService knowledgeSourceService;

    RagMcpService service;

    @BeforeEach
    void setUp() {
        // Default: mcp.email unset — search_knowledge must deny all sources.
        service = new RagMcpService(retrievalService, mcpConnectorService, knowledgeSourceService,
                new McpProperties("test-key", null));
    }

    // ── searchKnowledge — unconfigured mcp.email denies all sources ────────────

    @Test
    void searchKnowledge_emailNotConfigured_passesEmptyAllowedSources() {
        when(retrievalService.retrieve(eq("What is Java?"), eq(5), any(Map.class), eq(Set.of())))
                .thenReturn(List.of());

        service.searchKnowledge("What is Java?", 5);

        org.mockito.Mockito.verify(retrievalService)
                .retrieve(eq("What is Java?"), eq(5), any(Map.class), eq(Set.of()));
        org.mockito.Mockito.verifyNoInteractions(knowledgeSourceService);
    }

    // ── searchKnowledge — scoped to mcp.email's accessible sources ─────────────

    @Test
    void searchKnowledge_emailConfigured_scopesToAccessibleSources() {
        service = new RagMcpService(retrievalService, mcpConnectorService, knowledgeSourceService,
                new McpProperties("test-key", "mcp-bot@test.com"));
        KnowledgeSource ks = new KnowledgeSource("java.pdf", "java.pdf", null, 3, "mcp-bot@test.com", null);
        when(knowledgeSourceService.listAccessible("mcp-bot@test.com")).thenReturn(List.of(ks));
        DocumentResult doc = new DocumentResult("id-1", "Java is a language.", 0.95, "java.pdf", null);
        when(retrievalService.retrieve(eq("What is Java?"), eq(5), any(Map.class), eq(Set.of("java.pdf"))))
                .thenReturn(List.of(doc));

        String result = service.searchKnowledge("What is Java?", 5);

        assertThat(result).contains("java.pdf");
        assertThat(result).contains("Java is a language.");
        assertThat(result).contains("0.95");
    }

    @Test
    void searchKnowledge_noResults_returnsNoDocumentsMessage() {
        when(retrievalService.retrieve(anyString(), anyInt(), any(Map.class), eq(Set.of())))
                .thenReturn(List.of());

        String result = service.searchKnowledge("Random query", 5);

        assertThat(result).contains("No relevant documents found");
        assertThat(result).contains("Random query");
    }

    @Test
    void searchKnowledge_topKZero_defaultsToFive() {
        when(retrievalService.retrieve(anyString(), eq(5), any(Map.class), eq(Set.of())))
                .thenReturn(List.of());

        service.searchKnowledge("query", 0);

        org.mockito.Mockito.verify(retrievalService)
                .retrieve(anyString(), eq(5), any(Map.class), eq(Set.of()));
    }

    @Test
    void searchKnowledge_topKNegative_defaultsToFive() {
        when(retrievalService.retrieve(anyString(), eq(5), any(Map.class), eq(Set.of())))
                .thenReturn(List.of());

        service.searchKnowledge("query", -3);

        org.mockito.Mockito.verify(retrievalService)
                .retrieve(anyString(), eq(5), any(Map.class), eq(Set.of()));
    }

    @Test
    void searchKnowledge_topKTooLarge_capsAtTwenty() {
        when(retrievalService.retrieve(anyString(), eq(20), any(Map.class), eq(Set.of())))
                .thenReturn(List.of());

        service.searchKnowledge("query", 100);

        org.mockito.Mockito.verify(retrievalService)
                .retrieve(anyString(), eq(20), any(Map.class), eq(Set.of()));
    }

    @Test
    void searchKnowledge_multipleResults_separatedByDivider() {
        service = new RagMcpService(retrievalService, mcpConnectorService, knowledgeSourceService,
                new McpProperties("test-key", "mcp-bot@test.com"));
        when(knowledgeSourceService.listAccessible("mcp-bot@test.com"))
                .thenReturn(List.of(
                        new KnowledgeSource("a.pdf", "a.pdf", null, 3, "mcp-bot@test.com", null),
                        new KnowledgeSource("b.pdf", "b.pdf", null, 3, "mcp-bot@test.com", null)));
        DocumentResult d1 = new DocumentResult("1", "Content A.", 0.9, "a.pdf", null);
        DocumentResult d2 = new DocumentResult("2", "Content B.", 0.8, "b.pdf", null);
        when(retrievalService.retrieve(anyString(), anyInt(), any(Map.class), eq(Set.of("a.pdf", "b.pdf"))))
                .thenReturn(List.of(d1, d2));

        String result = service.searchKnowledge("query", 5);

        assertThat(result).contains("a.pdf");
        assertThat(result).contains("b.pdf");
        assertThat(result).contains("---");
    }

    // ── ingestUrl ─────────────────────────────────────────────────────────────

    @Test
    void ingestUrl_returnsFormattedMessage() {
        when(mcpConnectorService.fetchAndIngest("https://example.com", "docs", null))
                .thenReturn(new UrlIngestionResult("ingested", "https://example.com", "Example Page", 5));

        String result = service.ingestUrl("https://example.com", "docs");

        assertThat(result).contains("Example Page");
        assertThat(result).contains("https://example.com");
        assertThat(result).contains("5");
        assertThat(result).contains("chunks added");
    }

    @Test
    void ingestUrl_noCategory_passes() {
        when(mcpConnectorService.fetchAndIngest("https://example.com", null, null))
                .thenReturn(new UrlIngestionResult("ingested", "https://example.com", "Title", 3));

        String result = service.ingestUrl("https://example.com", null);

        assertThat(result).contains("3");
    }

    @Test
    void ingestUrl_emailConfigured_scopesToThatIdentity() {
        service = new RagMcpService(retrievalService, mcpConnectorService, knowledgeSourceService,
                new McpProperties("test-key", "mcp-bot@test.com"));
        when(mcpConnectorService.fetchAndIngest("https://example.com", "docs", "mcp-bot@test.com"))
                .thenReturn(new UrlIngestionResult("ingested", "https://example.com", "Example Page", 5));

        String result = service.ingestUrl("https://example.com", "docs");

        assertThat(result).contains("Example Page");
    }
}
