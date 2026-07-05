package com.agentsystem.agent.controller;

import com.agentsystem.agent.AgentSystemGraph;
import com.agentsystem.agent.state.AgentState;
import com.agentsystem.config.AgentMetrics;
import com.agentsystem.config.LlmProperties;
import com.agentsystem.conversation.service.ConversationService;
import com.agentsystem.conversation.entity.ConversationMessage;
import com.agentsystem.knowledge.service.KnowledgeSourceService;
import com.agentsystem.knowledge.entity.KnowledgeSource;
import com.agentsystem.mcp.service.McpConnectorService;
import com.agentsystem.rag.service.DocumentIngestionService;
import com.agentsystem.org.OrgContext;
import com.agentsystem.schema.AgentRequest;
import com.agentsystem.schema.AgentResponse;
import com.agentsystem.schema.UrlIngestionResult;
import com.agentsystem.user.service.UserPreferenceService;
import com.agentsystem.webfetch.service.WebFetchService;
import com.agentsystem.webfetch.entity.WebFetchWhitelist;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for the RAG agent system.
 *
 * POST /api/v1/agent/query                — run the full RAG agent pipeline
 * GET  /api/v1/agent/conversations/{id}   — fetch conversation history
 * POST /api/v1/agent/ingest               — ingest a document into Weaviate
 * POST /api/v1/agent/ingest/text          — ingest plain text into Weaviate
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@Tag(name = "RAG Agent", description = "LangGraph4j-powered RAG agent endpoints")
public class AgentController {

    private final AgentSystemGraph            agentGraph;
    private final DocumentIngestionService ingestionService;
    private final McpConnectorService      mcpConnectorService;
    private final ConversationService      conversationService;
    private final KnowledgeSourceService   knowledgeSourceService;
    private final WebFetchService          webFetchService;
    private final UserPreferenceService    userPreferenceService;
    private final LlmProperties            llmProperties;
    private final AgentMetrics             agentMetrics;

    // ── Query ─────────────────────────────────────────────────────────────────

    @PostMapping(value = "/query", consumes = MediaType.APPLICATION_JSON_VALUE,
                                   produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Run a query through the RAG agent pipeline")
    public ResponseEntity<AgentResponse> query(@RequestBody @Valid AgentRequest request,
                                               HttpServletRequest httpRequest) {

        String runId = UUID.randomUUID().toString();
        log.info("[AgentController] Received query runId={} query='{}'", runId, request.query());

        OrgContext ctx = OrgContext.from(httpRequest);

        // ── Resolve / create conversation ──────────────────────────────────
        String conversationId = conversationService.resolveConversation(
                request.conversationId(), ctx);

        conversationService.saveUserMessage(conversationId, request.query());

        long startMs = System.currentTimeMillis();
        try {
            // Seed initial state
            Map<String, Object> initData = new HashMap<>();
            initData.put("request", request);
            initData.put("runId", runId);
            if (ctx.userUuid() != null) {
                initData.put("userUuid", ctx.userUuid());
                initData.put("orgId",    ctx.orgId());
                initData.put("mode",     ctx.mode());
            }
            // Model priority: conversation → user default → configured DEFAULT_MODEL → raw provider
            String selectedModel = conversationService.getConversationModel(conversationId);
            if (selectedModel == null && ctx.userUuid() != null) {
                selectedModel = userPreferenceService.getSelectedModel(ctx.userUuid());
            }
            if (selectedModel == null) {
                String dm = llmProperties.getDefaultModel();
                if (dm != null && !dm.isBlank()) selectedModel = dm;
            }
            if (selectedModel != null) {
                initData.put("selectedModelDisplayName", selectedModel);
            }

            // Invoke the compiled LangGraph
            var result = agentGraph.getGraph()
                    .invoke(initData, RunnableConfig.builder().build());

            AgentState finalState = result.orElseThrow(
                    () -> new RuntimeException("Graph produced no output"));

            AgentResponse raw = finalState.response().orElseThrow(
                    () -> new RuntimeException("No response in final state"));

            // Persist assistant answer and inject conversationId into metadata
            conversationService.saveAssistantMessage(conversationId, raw.answer(), runId);
            AgentResponse response = withConversationId(raw, conversationId);

            agentMetrics.recordQuery(response.fallbackActivated(), System.currentTimeMillis() - startMs);
            log.info("[AgentController] Completed runId={} conversationId={} fallback={}",
                    runId, conversationId, response.fallbackActivated());
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            agentMetrics.recordQueryError();
            log.error("[AgentController] Pipeline error runId={}: {}", runId, ex.getMessage(), ex);
            return ResponseEntity.internalServerError()
                    .body(buildErrorResponse(runId, request.query(), ex, conversationId));
        }
    }

    // ── Conversation history ──────────────────────────────────────────────────

    @GetMapping("/conversations")
    @Operation(summary = "List active (non-archived) conversations for the authenticated user")
    public ResponseEntity<List<com.agentsystem.conversation.entity.Conversation>> listConversations(
            HttpServletRequest httpRequest) {
        OrgContext ctx = OrgContext.from(httpRequest);
        if (ctx.userUuid() == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(conversationService.listConversations(ctx));
    }

    @GetMapping("/conversations/archived")
    @Operation(summary = "List archived conversations for the authenticated user")
    public ResponseEntity<List<com.agentsystem.conversation.entity.Conversation>> listArchivedConversations(
            HttpServletRequest httpRequest) {
        OrgContext ctx = OrgContext.from(httpRequest);
        if (ctx.userUuid() == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(conversationService.listArchivedConversations(ctx));
    }

    @PatchMapping("/conversations/{conversationId}/model")
    @Operation(summary = "Set the model for a conversation (owner only). Pass null to reset to user/system default.")
    public ResponseEntity<Map<String, Object>> setConversationModel(
            @PathVariable String conversationId,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        String userUuid = (String) httpRequest.getAttribute("authenticatedUserUuid");
        String displayName = body.get("selectedModel");
        try {
            var conv = conversationService.setConversationModel(conversationId, userUuid, displayName);
            return ResponseEntity.ok(Map.of(
                    "conversationId", conv.getId(),
                    "selectedModel", conv.getSelectedModel() != null ? conv.getSelectedModel() : ""
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/conversations/{conversationId}/archive")
    @Operation(summary = "Archive a conversation (owner only)")
    public ResponseEntity<Void> archiveConversation(
            @PathVariable String conversationId,
            HttpServletRequest httpRequest) {
        String userUuid = (String) httpRequest.getAttribute("authenticatedUserUuid");
        try {
            conversationService.setArchived(conversationId, userUuid, true);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/conversations/{conversationId}/unarchive")
    @Operation(summary = "Unarchive a conversation (owner only)")
    public ResponseEntity<Void> unarchiveConversation(
            @PathVariable String conversationId,
            HttpServletRequest httpRequest) {
        String userUuid = (String) httpRequest.getAttribute("authenticatedUserUuid");
        try {
            conversationService.setArchived(conversationId, userUuid, false);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/conversations/{conversationId}")
    @Operation(summary = "Retrieve the full message history for a conversation")
    public ResponseEntity<List<ConversationMessage>> conversationHistory(
            @PathVariable String conversationId) {
        List<ConversationMessage> messages = conversationService.getMessages(conversationId);
        if (messages.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(messages);
    }

    @DeleteMapping("/conversations/{conversationId}")
    @Operation(summary = "Delete a conversation and all its messages (owner only)")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable String conversationId,
            HttpServletRequest httpRequest) {
        String userUuid = (String) httpRequest.getAttribute("authenticatedUserUuid");
        try {
            conversationService.deleteConversation(conversationId, userUuid);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Conversation share links ──────────────────────────────────────────────

    /**
     * Create or replace a share link for a conversation.
     * Body: {
     *   "expireDays": 7,          // null = never
     *   "accessType": "EVERYONE"  | "WHITELIST",
     *   "whitelist":  ["a@b.com"] // required when accessType=WHITELIST
     * }
     */
    @PostMapping("/conversations/{conversationId}/share")
    @Operation(summary = "Create a shareable link for a conversation (owner only)")
    public ResponseEntity<Map<String, Object>> createShare(
            @PathVariable String conversationId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest httpRequest) {

        String userUuid = (String) httpRequest.getAttribute("authenticatedUserUuid");

        Integer expireDays = null;
        String accessType  = "EVERYONE";
        java.util.List<String> whitelist = java.util.List.of();

        if (body != null) {
            if (body.get("expireDays") instanceof Number n) expireDays = n.intValue();
            if (body.get("accessType") instanceof String s) accessType = s;
            if (body.get("whitelist")  instanceof java.util.List<?> l) {
                whitelist = l.stream()
                        .filter(e -> e instanceof String)
                        .map(e -> (String) e)
                        .toList();
            }
        }

        try {
            var share = conversationService.createShare(
                    conversationId, userUuid, expireDays, "READ_ONLY", accessType, whitelist);
            return ResponseEntity.ok(shareToMap(share));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Get the current share link for a conversation (owner only). */
    @GetMapping("/conversations/{conversationId}/share")
    @Operation(summary = "Get the current share link for a conversation (owner only)")
    public ResponseEntity<Map<String, Object>> getShare(
            @PathVariable String conversationId,
            HttpServletRequest httpRequest) {

        String userUuid = (String) httpRequest.getAttribute("authenticatedUserUuid");
        try {
            var share = conversationService.getShare(conversationId, userUuid);
            return ResponseEntity.ok(shareToMap(share));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Revoke (delete) the share link for a conversation (owner only). */
    @DeleteMapping("/conversations/{conversationId}/share")
    @Operation(summary = "Revoke the share link for a conversation (owner only)")
    public ResponseEntity<Void> revokeShare(
            @PathVariable String conversationId,
            HttpServletRequest httpRequest) {

        String userUuid = (String) httpRequest.getAttribute("authenticatedUserUuid");
        try {
            conversationService.revokeShare(conversationId, userUuid);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private Map<String, Object> shareToMap(com.agentsystem.conversation.entity.ConversationShare share) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("token",          share.getToken());
        result.put("conversationId", share.getConversationId());
        result.put("expiresAt",      share.getExpiresAt());
        result.put("createdAt",      share.getCreatedAt());
        result.put("shareMode",      share.getShareMode());
        result.put("accessType",     share.getAccessType());
        result.put("whitelist",      share.getWhitelist());
        return result;
    }

    // ── Ingestion ─────────────────────────────────────────────────────────────

    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Ingest a file (PDF, DOCX, HTML, TXT…) into Weaviate")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "source",   required = false) String source,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "replace",  required = false, defaultValue = "false") boolean replace,
            HttpServletRequest httpRequest) throws Exception {

        Map<String, Object> metadata = new HashMap<>();
        if (source   != null) metadata.put("source", source);
        if (category != null) metadata.put("category", category);

        Resource resource  = file.getResource();
        int      chunkCount = ingestionService.ingest(resource, metadata, replace);

        OrgContext ctx = OrgContext.from(httpRequest);
        String sourceKey = source != null ? source : file.getOriginalFilename();
        knowledgeSourceService.upsert(sourceKey, file.getOriginalFilename(), category, chunkCount, ctx);
        agentMetrics.recordIngest();

        return ResponseEntity.ok(Map.of(
                "status",     "ingested",
                "filename",   file.getOriginalFilename(),
                "chunkCount", chunkCount
        ));
    }

    @PostMapping("/ingest/url")
    @Operation(summary = "Fetch a URL and ingest its content into Weaviate")
    public ResponseEntity<UrlIngestionResult> ingestUrl(
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {

        String url      = body.get("url");
        String category = body.get("category");
        OrgContext ctx  = OrgContext.from(httpRequest);

        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        UrlIngestionResult result = mcpConnectorService.fetchAndIngest(url, category, ctx);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/ingest/text")
    @Operation(summary = "Ingest plain text directly into Weaviate")
    public ResponseEntity<Map<String, Object>> ingestText(
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {

        String text     = body.getOrDefault("text", "");
        String sourceId = body.getOrDefault("source", "api-text-" + UUID.randomUUID());

        if (text.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "text field must not be empty"));
        }

        boolean replace = Boolean.parseBoolean(body.getOrDefault("replace", "false"));
        OrgContext ctx  = OrgContext.from(httpRequest);
        int chunkCount = ingestionService.ingestText(text, sourceId, Map.of(), replace);
        knowledgeSourceService.upsert(sourceId, sourceId, null, chunkCount, ctx);
        return ResponseEntity.ok(Map.of(
                "status",     "ingested",
                "source",     sourceId,
                "chunkCount", chunkCount
        ));
    }

    // ── Knowledge management ──────────────────────────────────────────────────

    @GetMapping("/knowledge")
    @Operation(summary = "List knowledge sources accessible to the authenticated user")
    public ResponseEntity<List<KnowledgeSource>> listKnowledge(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(knowledgeSourceService.listAccessible(OrgContext.from(httpRequest)));
    }

    @DeleteMapping("/knowledge")
    @Operation(summary = "Delete all chunks for a source (owner only)")
    public ResponseEntity<Map<String, String>> deleteKnowledge(
            @RequestParam("source") String source,
            HttpServletRequest httpRequest) {
        try {
            knowledgeSourceService.delete(source, OrgContext.from(httpRequest));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/knowledge")
    @Operation(summary = "Update label / category for a knowledge source (owner only)")
    public ResponseEntity<KnowledgeSource> updateKnowledge(
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        String source   = (String) body.get("source");
        String label    = (String) body.get("label");
        String category = (String) body.get("category");
        if (source == null || source.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            KnowledgeSource updated = knowledgeSourceService.updateMetadata(
                    source, label, category, OrgContext.from(httpRequest));
            return ResponseEntity.ok(updated);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/knowledge/share")
    @Operation(summary = "Update the shared-email list for a knowledge source (owner only)")
    public ResponseEntity<Map<String, Object>> shareKnowledge(
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        OrgContext ctx = OrgContext.from(httpRequest);
        String source = (String) body.get("source");
        @SuppressWarnings("unchecked")
        java.util.List<String> emails = (java.util.List<String>) body.getOrDefault("emails", java.util.List.of());
        try {
            var updated = knowledgeSourceService.updateSharing(source, emails, ctx);
            return ResponseEntity.ok(Map.of(
                    "source",      updated.getSource(),
                    "sharedUuids", updated.sharedUuids()
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Web-fetch whitelist ───────────────────────────────────────────────────

    @GetMapping("/web-fetch/whitelist")
    @Operation(summary = "List whitelisted domains for web fetch")
    public ResponseEntity<List<WebFetchWhitelist>> listWebFetchWhitelist(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(webFetchService.listWhitelist(OrgContext.from(httpRequest)));
    }

    @PostMapping("/web-fetch/whitelist")
    @Operation(summary = "Add a domain to the web-fetch whitelist")
    public ResponseEntity<WebFetchWhitelist> addWebFetchDomain(
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {

        String domain = body.get("domain");
        if (domain == null || domain.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(webFetchService.addDomain(domain, OrgContext.from(httpRequest)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/web-fetch/whitelist/{domain}")
    @Operation(summary = "Remove a domain from the web-fetch whitelist")
    public ResponseEntity<Void> removeWebFetchDomain(@PathVariable String domain,
                                                      HttpServletRequest httpRequest) {
        try {
            webFetchService.removeDomain(domain, OrgContext.from(httpRequest));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Rebuild the response with conversationId stamped into RunMetadata. */
    private AgentResponse withConversationId(AgentResponse raw, String conversationId) {
        AgentResponse.RunMetadata meta = raw.metadata();
        return new AgentResponse(
                raw.answer(),
                raw.sources(),
                raw.routeDecision(),
                raw.fallbackActivated(),
                raw.fallbackReason(),
                new AgentResponse.RunMetadata(
                        meta.runId(),
                        meta.startedAt(),
                        meta.durationMs(),
                        meta.documentsRetrieved(),
                        meta.modelUsed(),
                        conversationId
                )
        );
    }

    private AgentResponse buildErrorResponse(String runId, String query, Exception ex,
                                             String conversationId) {
        return new AgentResponse(
                "An internal error occurred: " + ex.getMessage(),
                java.util.List.of(),
                new AgentResponse.RouteDecision("ERROR", ex.getMessage(), 0.0),
                true,
                ex.getMessage(),
                new AgentResponse.RunMetadata(runId, java.time.Instant.now(), 0L, 0, "error",
                        conversationId)
        );
    }
}
