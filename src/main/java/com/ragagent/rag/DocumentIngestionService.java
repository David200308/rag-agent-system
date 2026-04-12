package com.ragagent.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Ingests documents into Weaviate.
 *
 * Pipeline:
 *   Resource → TikaDocumentReader (PDF, DOCX, HTML…)
 *            → TokenTextSplitter  (chunk by tokens)
 *            → VectorStore.add    (embed + upsert to Weaviate)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStore vectorStore;

    @Value("${spring.ai.rag.chunk-size:1000}")
    private int chunkSize;

    @Value("${spring.ai.rag.chunk-overlap:200}")
    private int chunkOverlap;

    /**
     * Ingest a single file resource with optional metadata.
     */
    public int ingest(Resource resource, Map<String, Object> metadata) {
        log.info("[DocumentIngestionService] Ingesting: {}", resource.getFilename());

        var reader   = new TikaDocumentReader(resource);
        var splitter = new TokenTextSplitter(chunkSize, chunkOverlap, 5, 10000, true);

        List<Document> rawDocs  = reader.read();
        List<Document> chunks   = splitter.apply(rawDocs);

        // Enrich each chunk with caller-supplied metadata
        if (metadata != null && !metadata.isEmpty()) {
            chunks = chunks.stream()
                    .map(d -> {
                        d.getMetadata().putAll(metadata);
                        return d;
                    })
                    .toList();
        }

        vectorStore.add(chunks);
        log.info("[DocumentIngestionService] Ingested {} chunks from {}", chunks.size(),
                resource.getFilename());
        return chunks.size();
    }

    /**
     * Ingest plain text directly (e.g. from a web scrape or API response).
     */
    public int ingestText(String text, String sourceId, Map<String, Object> metadata) {
        var meta = metadata != null ? new java.util.HashMap<>(metadata) : new java.util.HashMap<String, Object>();
        meta.put("source", sourceId);

        var doc      = new Document(text, meta);
        var splitter = new TokenTextSplitter(chunkSize, chunkOverlap, 5, 10000, true);
        var chunks   = splitter.apply(List.of(doc));

        vectorStore.add(chunks);
        log.info("[DocumentIngestionService] Ingested {} text chunks from {}", chunks.size(), sourceId);
        return chunks.size();
    }
}
