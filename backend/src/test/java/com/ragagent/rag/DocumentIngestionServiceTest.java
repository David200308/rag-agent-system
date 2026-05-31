package com.ragagent.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock VectorStore vectorStore;

    DocumentIngestionService service;

    @BeforeEach
    void setUp() {
        service = new DocumentIngestionService(vectorStore);
        ReflectionTestUtils.setField(service, "chunkSize",    500);
        ReflectionTestUtils.setField(service, "chunkOverlap", 50);
    }

    // ── ingestText ────────────────────────────────────────────────────────────

    @Test
    void ingestText_addsDocumentsToVectorStore() {
        String text = "This is a test document with some content to be ingested.";

        int chunks = service.ingestText(text, "test-source", null, false);

        assertThat(chunks).isGreaterThan(0);
        verify(vectorStore).add(anyList());
    }

    @Test
    void ingestText_withMetadata_enrichesChunks() {
        String text = "Document content.";
        Map<String, Object> meta = Map.of("category", "test", "author", "Alice");

        service.ingestText(text, "my-source", meta, false);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        List<Document> docs = captor.getValue();
        assertThat(docs).isNotEmpty();
        assertThat(docs.get(0).getMetadata()).containsEntry("source", "my-source");
        assertThat(docs.get(0).getMetadata()).containsEntry("category", "test");
        assertThat(docs.get(0).getMetadata()).containsEntry("author", "Alice");
    }

    @Test
    void ingestText_replace_deletesBeforeIngest() {
        service.ingestText("New content.", "old-source", null, true);

        // deleteBySource should be called (vectorStore.delete invoked)
        verify(vectorStore).delete(any(Expression.class));
        verify(vectorStore).add(anyList());
    }

    @Test
    void ingestText_noReplace_doesNotDelete() {
        service.ingestText("Content here.", "some-source", null, false);

        verify(vectorStore, never()).delete(any(Expression.class));
        verify(vectorStore).add(anyList());
    }

    @Test
    void ingestText_noMetadata_sourceIsStillSet() {
        service.ingestText("Hello world.", "my-url", null, false);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        assertThat(captor.getValue().get(0).getMetadata()).containsEntry("source", "my-url");
    }

    @Test
    void ingestText_returnsChunkCount() {
        String longText = "word ".repeat(2000);  // ~2000 words to force multiple chunks

        int count = service.ingestText(longText, "big-doc", null, false);

        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    // ── deleteBySource ────────────────────────────────────────────────────────

    @Test
    void deleteBySource_invokesVectorStoreDelete() {
        service.deleteBySource("my-source");

        verify(vectorStore).delete(any(Expression.class));
    }

    @Test
    void deleteBySource_vectorStoreThrows_doesNotPropagate() {
        doThrow(new RuntimeException("Weaviate error")).when(vectorStore).delete(any(Expression.class));

        // Should not throw — errors are swallowed and logged
        service.deleteBySource("bad-source");
    }

    @Test
    void deleteBySource_calledOnceWithFilter() {
        service.deleteBySource("test-source");

        verify(vectorStore, times(1)).delete(any(Expression.class));
    }

    // ── ingestText — replace with custom source in metadata ───────────────────

    @Test
    void ingestText_replaceWithCustomSourceInMeta_usesMetaSource() {
        Map<String, Object> meta = Map.of("source", "custom-source-id");
        service.ingestText("Text content.", "fallback-source", meta, true);

        // vectorStore.delete should be called (with the custom source from metadata)
        verify(vectorStore).delete(any(Expression.class));
    }
}
