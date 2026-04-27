package com.ragagent.rag;

import com.ragagent.schema.DocumentResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    @Mock VectorStore vectorStore;

    @InjectMocks RetrievalService retrievalService;

    // ── Security guard: empty allowedSources ──────────────────────────────────

    @Test
    void retrieve_emptyAllowedSources_returnsEmptyWithoutCallingVectorStore() {
        List<DocumentResult> result = retrievalService.retrieve(
                "test query", 5, Map.of(), Set.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(vectorStore);
    }

    // ── Null allowedSources (auth disabled) ───────────────────────────────────

    @Test
    void retrieve_nullAllowedSources_queriesVectorStore() {
        Document doc = new Document("doc-1", "Some content", Map.of("source", "file.pdf"));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc));

        List<DocumentResult> result = retrievalService.retrieve(
                "test query", 5, Map.of(), null);

        verify(vectorStore).similaritySearch(any(SearchRequest.class));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("doc-1");
        assertThat(result.get(0).source()).isEqualTo("file.pdf");
    }

    // ── Filter by score ───────────────────────────────────────────────────────

    @Test
    void retrieve_documentWithDistanceOne_isFilteredOut() {
        // distance=1.0 → similarity=0.0, which fails the score>0 filter
        Document zeroScore = new Document("z", "content",
                Map.of("source", "s", "distance", 1.0));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(zeroScore));

        List<DocumentResult> result = retrievalService.retrieve(
                "query", 5, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void retrieve_documentWithNoDistance_hasSimilarityOne() {
        // distance defaults to 0.0 → similarity = 1.0 - 0.0 = 1.0
        Document doc = new Document("d1", "content", Map.of("source", "s"));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc));

        List<DocumentResult> result = retrievalService.retrieve(
                "query", 5, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isEqualTo(1.0);
    }

    // ── topK forwarded to SearchRequest ──────────────────────────────────────

    @Test
    void retrieve_forwardsTopKToSearchRequest() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);

        retrievalService.retrieve("query", 7, null, null);

        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getTopK()).isEqualTo(7);
    }

    // ── Allowed sources (non-empty set) ───────────────────────────────────────

    @Test
    void retrieve_withAllowedSources_callsVectorStoreWithFilter() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        retrievalService.retrieve("query", 5, null, Set.of("doc1.pdf", "doc2.pdf"));

        // Just verify it passes through to the vector store (filter built internally)
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    @Test
    void retrievalFallback_returnsEmptyList() {
        List<DocumentResult> result = retrievalService.retrievalFallback(
                "query", 5, null, null, new RuntimeException("Weaviate down"));

        assertThat(result).isEmpty();
    }

    // ── Source metadata default ───────────────────────────────────────────────

    @Test
    void retrieve_missingSourceMetadata_defaultsToUnknown() {
        Document doc = new Document("id1", "content", Map.of());
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc));

        List<DocumentResult> result = retrievalService.retrieve(
                "query", 5, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).source()).isEqualTo("unknown");
    }

    // ── Empty VectorStore response ────────────────────────────────────────────

    @Test
    void retrieve_vectorStoreReturnsEmpty_returnsEmpty() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        List<DocumentResult> result = retrievalService.retrieve(
                "nothing here", 5, null, null);

        assertThat(result).isEmpty();
    }
}
