package com.ragagent.controller;

import com.ragagent.agent.RagAgentGraph;
import com.ragagent.agent.state.AgentState;
import com.ragagent.auth.service.AuthService;
import com.ragagent.conversation.ConversationService;
import com.ragagent.conversation.entity.ConversationMessage;
import com.ragagent.conversation.entity.ConversationShare;
import com.ragagent.schema.AgentRequest;
import com.ragagent.schema.AgentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public endpoint for reading and interacting with shared conversations.
 *
 * Mounted at /api/v1/share/** which is exempt from AuthFilter.
 * Interactive endpoints perform manual JWT validation via AuthService.
 */
@RestController
@RequestMapping("/api/v1/share")
@RequiredArgsConstructor
@Tag(name = "Share", description = "Public access to shared conversations")
public class ShareController {

    private final ConversationService conversationService;
    private final AuthService         authService;
    private final RagAgentGraph       agentGraph;

    // ── Response record ───────────────────────────────────────────────────────

    public record ShareMetaResponse(
        String shareMode,
        String accessType,
        String ownerEmail,
        String expiresAt,
        List<ConversationMessage> messages
    ) {}

    // ── GET /{token} — read share metadata + messages (no auth required) ──────

    @GetMapping("/{token}")
    @Operation(summary = "Read share metadata and messages (public, no auth required)")
    public ResponseEntity<ShareMetaResponse> readShared(@PathVariable String token) {
        try {
            ConversationShare share = conversationService.getShareByToken(token);
            List<ConversationMessage> messages =
                    conversationService.getMessages(share.getConversationId());
            return ResponseEntity.ok(new ShareMetaResponse(
                share.getShareMode(),
                share.getAccessType(),
                share.getOwnerEmail(),
                share.getExpiresAt() != null ? share.getExpiresAt().toString() : null,
                messages
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── POST /{token}/query — interactive query (auth required) ──────────────

    @PostMapping("/{token}/query")
    @Operation(summary = "Submit a query in an interactive shared conversation (login required)")
    public ResponseEntity<?> submitSharedQuery(
            @PathVariable String token,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {

        // Manual JWT extraction — /share/** is exempt from AuthFilter
        String visitorEmail = resolveEmail(httpRequest);

        // Validate share access
        ConversationShare share;
        try {
            share = conversationService.validateShareAccess(token, visitorEmail);
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }

        if (!"INTERACTIVE".equals(share.getShareMode())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "This shared conversation is read-only."));
        }

        String query = body.get("query") instanceof String q ? q.trim() : "";
        if (query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query must not be blank."));
        }

        String conversationId = share.getConversationId();
        String runId          = UUID.randomUUID().toString();

        conversationService.saveUserMessage(conversationId, query);

        try {
            List<AgentRequest.ConversationTurn> history =
                    conversationService.loadHistory(conversationId);

            AgentRequest agentRequest = new AgentRequest(
                    query, null, null, history, false, conversationId,
                    null, true, true);

            Map<String, Object> initData = new HashMap<>();
            initData.put("request",         agentRequest);
            initData.put("runId",           runId);
            if (visitorEmail != null) {
                initData.put("userEmail", visitorEmail);
            }
            initData.put("shareOwnerEmail", share.getOwnerEmail());

            var result = agentGraph.getGraph()
                    .invoke(initData, RunnableConfig.builder().build());

            AgentState finalState = result.orElseThrow(
                    () -> new RuntimeException("Graph produced no output"));
            AgentResponse raw = finalState.response().orElseThrow(
                    () -> new RuntimeException("No response in final state"));

            conversationService.saveAssistantMessage(conversationId, raw.answer(), runId);

            AgentResponse response = withConversationId(raw, conversationId);
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveEmail(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String jwt = header.substring(7);
            try { return authService.validateToken(jwt); } catch (Exception ignored) {}
        }
        return null;
    }

    private AgentResponse withConversationId(AgentResponse raw, String conversationId) {
        AgentResponse.RunMetadata meta = raw.metadata();
        return new AgentResponse(
                raw.answer(), raw.sources(), raw.routeDecision(),
                raw.fallbackActivated(), raw.fallbackReason(),
                new AgentResponse.RunMetadata(
                        meta.runId(), meta.startedAt(), meta.durationMs(),
                        meta.documentsRetrieved(), meta.modelUsed(), conversationId));
    }
}
