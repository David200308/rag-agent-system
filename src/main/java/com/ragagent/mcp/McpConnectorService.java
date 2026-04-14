package com.ragagent.mcp;

import com.ragagent.knowledge.KnowledgeSourceService;
import com.ragagent.rag.DocumentIngestionService;
import com.ragagent.schema.UrlIngestionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Fetches a URL, extracts clean text via Jsoup, and ingests the content into Weaviate.
 *
 * Used by both:
 *   - REST endpoint  POST /api/v1/agent/ingest/url
 *   - MCP tool       ingest_url (exposed via RagMcpService)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpConnectorService {

    private final DocumentIngestionService ingestionService;
    private final KnowledgeSourceService   knowledgeSourceService;
    private final RestClient.Builder       restClientBuilder;

    /**
     * Fetch {@code url}, strip HTML, and ingest the resulting text into Weaviate.
     *
     * @param url      the page to fetch (http/https)
     * @param category optional metadata category tag (may be null)
     */
    public UrlIngestionResult fetchAndIngest(String url, String category) {
        return fetchAndIngest(url, category, null);
    }

    public UrlIngestionResult fetchAndIngest(String url, String category, String ownerEmail) {
        log.info("[McpConnectorService] Fetching URL: {}", url);

        // ── 1. Fetch raw HTML ────────────────────────────────────────────────
        String html = restClientBuilder.build()
                .get()
                .uri(url)
                .header("User-Agent", "RAG-Agent-MCP-Connector/1.0")
                .retrieve()
                .body(String.class);

        if (html == null || html.isBlank()) {
            throw new IllegalStateException("Empty response from URL: " + url);
        }

        // ── 2. Parse & extract text ──────────────────────────────────────────
        Document doc   = Jsoup.parse(html, url);
        String   title = doc.title();
        String   text  = doc.body().text();   // strips all HTML tags

        // ── 3. Ingest into Weaviate ──────────────────────────────────────────
        Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("source", url);
        meta.put("title", title);
        if (category != null && !category.isBlank()) {
            meta.put("category", category);
        }

        int chunks = ingestionService.ingestText(text, url, meta, false);
        knowledgeSourceService.upsert(url, title.isBlank() ? url : title, category, chunks, ownerEmail);
        log.info("[McpConnectorService] Ingested {} chunks from {}", chunks, url);

        return new UrlIngestionResult("ingested", url, title, chunks);
    }
}
