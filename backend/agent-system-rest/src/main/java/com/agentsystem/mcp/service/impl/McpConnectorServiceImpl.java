package com.agentsystem.mcp.service.impl;

import com.agentsystem.mcp.service.McpConnectorService;

import com.agentsystem.knowledge.service.KnowledgeSourceService;
import com.agentsystem.org.OrgContext;
import com.agentsystem.rag.service.DocumentIngestionService;
import com.agentsystem.schema.UrlIngestionResult;
import com.agentsystem.user.entity.User;
import com.agentsystem.user.service.UserAccountService;
import com.agentsystem.webfetch.service.WebFetchService;
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
 *
 * Both callers are gated by the same domain whitelist WebFetchService enforces for
 * /api/v1/agent/web-fetch — without it, this fetches any attacker-supplied URL
 * (internal services, cloud metadata endpoints, etc.) with no restriction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpConnectorServiceImpl implements McpConnectorService {

    private final DocumentIngestionService ingestionService;
    private final KnowledgeSourceService   knowledgeSourceService;
    private final WebFetchService          webFetchService;
    private final RestClient.Builder       restClientBuilder;
    private final UserAccountService       userAccountService;

    /**
     * Fetch {@code url}, strip HTML, and ingest the resulting text into Weaviate.
     *
     * @param url      the page to fetch (http/https)
     * @param category optional metadata category tag (may be null)
     */
    @Override
    public UrlIngestionResult fetchAndIngest(String url, String category) {
        return fetchAndIngest(url, category, (OrgContext) null);
    }

    @Override
    public UrlIngestionResult fetchAndIngest(String url, String category, String ownerEmail) {
        return fetchAndIngest(url, category, new OrgContext(resolveUuid(ownerEmail), ownerEmail, "PERSONAL", null));
    }

    @Override
    public UrlIngestionResult fetchAndIngest(String url, String category, OrgContext ctx) {
        log.info("[McpConnectorService] Fetching URL: {}", url);

        if (!webFetchService.isUrlAllowed(url, ctx)) {
            throw new IllegalStateException(
                    "Domain not whitelisted: " + url + ". Add it via POST /api/v1/agent/web-fetch/whitelist");
        }

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
        knowledgeSourceService.upsert(url, title.isBlank() ? url : title, category, chunks, ctx);
        log.info("[McpConnectorService] Ingested {} chunks from {}", chunks, url);

        return new UrlIngestionResult("ingested", url, title, chunks);
    }

    /** Resolves an email to its user_uuid, or null if no such email is a registered user. */
    private String resolveUuid(String email) {
        if (email == null || email.isBlank()) return null;
        return userAccountService.findByEmail(email).map(User::getUuid).orElse(null);
    }
}
