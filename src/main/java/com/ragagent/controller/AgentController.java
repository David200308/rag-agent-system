package com.ragagent.controller;

import com.ragagent.agent.RagAgentGraph;
import com.ragagent.agent.state.AgentState;
import com.ragagent.conversation.ConversationService;
import com.ragagent.conversation.entity.ConversationMessage;
import com.ragagent.knowledge.KnowledgeSourceService;
import com.ragagent.knowledge.entity.KnowledgeSource;
import com.ragagent.mcp.McpConnectorService;
import com.ragagent.rag.DocumentIngestionService;
import com.ragagent.schema.AgentRequest;
import com.ragagent.schema.AgentResponse;
import com.ragagent.schema.UrlIngestionResult;
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

    private final RagAgentGraph            agentGraph;
    private final DocumentIngestionService ingestionService;
    private final McpConnectorService      mcpConnectorService;
    private final ConversationService      conversationService;
    private final KnowledgeSourceService   knowledgeSourceService;

    // ── Query ─────────────────────────────────────────────────────────────────

    @PostMapping(value = "/query", consumes = MediaType.APPLICATION_JSON_VALUE,
                                   produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Run a query through the RAG agent pipeline")
    public ResponseEntity<AgentResponse> query(@RequestBody @Valid AgentRequest request,
                                               HttpServletRequest httpRequest) {

        String runId = UUID.randomUUID().toString();
        log.info("[AgentController] Received query runId={} query='{}'", runId, request.query());

        String userEmail = (String) httpRequest.getAttribute("authenticatedEmail");

        // ── Resolve / create conversation ──────────────────────────────────
        String conversationId = conversationService.resolveConversation(
                request.conversationId(), userEmail);

        conversationService.saveUserMessage(conversationId, request.query());

        try {
            // Seed initial state
            Map<String, Object> initData = new HashMap<>();
            initData.put("request", request);
            initData.put("runId", runId);

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

            log.info("[AgentController] Completed runId={} conversationId={} fallback={}",
                    runId, conversationId, response.fallbackActivated());
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            log.error("[AgentController] Pipeline error runId={}: {}", runId, ex.getMessage(), ex);
            return ResponseEntity.internalServerError()
                    .body(buildErrorResponse(runId, request.query(), ex, conversationId));
        }
    }

    // ── Conversation history ──────────────────────────────────────────────────

    @GetMapping("/conversations")
    @Operation(summary = "List all conversations for the authenticated user")
    public ResponseEntity<List<com.ragagent.conversation.entity.Conversation>> listConversations(
            HttpServletRequest httpRequest) {
        String userEmail = (String) httpRequest.getAttribute("authenticatedEmail");
        if (userEmail == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(conversationService.listConversations(userEmail));
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

        String ownerEmail = (String) httpRequest.getAttribute("authenticatedEmail");
        String sourceKey = source != null ? source : file.getOriginalFilename();
        knowledgeSourceService.upsert(sourceKey, file.getOriginalFilename(), category, chunkCount, ownerEmail);

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

        String url        = body.get("url");
        String category   = body.get("category");
        String ownerEmail = (String) httpRequest.getAttribute("authenticatedEmail");

        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        UrlIngestionResult result = mcpConnectorService.fetchAndIngest(url, category, ownerEmail);
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
        String ownerEmail = (String) httpRequest.getAttribute("authenticatedEmail");
        int chunkCount = ingestionService.ingestText(text, sourceId, Map.of(), replace);
        knowledgeSourceService.upsert(sourceId, sourceId, null, chunkCount, ownerEmail);
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
        String email = (String) httpRequest.getAttribute("authenticatedEmail");
        return ResponseEntity.ok(knowledgeSourceService.listAccessible(email));
    }

    @DeleteMapping("/knowledge")
    @Operation(summary = "Delete all chunks for a source (owner only)")
    public ResponseEntity<Map<String, String>> deleteKnowledge(
            @RequestParam("source") String source,
            HttpServletRequest httpRequest) {
        String email = (String) httpRequest.getAttribute("authenticatedEmail");
        try {
            knowledgeSourceService.delete(source, email);
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
        String email    = (String) httpRequest.getAttribute("authenticatedEmail");
        String source   = (String) body.get("source");
        String label    = (String) body.get("label");
        String category = (String) body.get("category");
        if (source == null || source.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            KnowledgeSource updated = knowledgeSourceService.updateMetadata(source, label, category, email);
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
        String email  = (String) httpRequest.getAttribute("authenticatedEmail");
        String source = (String) body.get("source");
        @SuppressWarnings("unchecked")
        java.util.List<String> emails = (java.util.List<String>) body.getOrDefault("emails", java.util.List.of());
        try {
            var updated = knowledgeSourceService.updateSharing(source, emails, email);
            return ResponseEntity.ok(Map.of(
                    "source",       updated.getSource(),
                    "sharedEmails", updated.sharedEmails()
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
