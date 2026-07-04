package com.agentsystem.agent.controller;

import com.agentsystem.agent.AgentSystemGraph;
import com.agentsystem.config.AgentMetrics;
import com.agentsystem.org.OrgContext;
import com.agentsystem.config.LlmProperties;
import com.agentsystem.conversation.service.ConversationService;
import com.agentsystem.conversation.entity.Conversation;
import com.agentsystem.conversation.entity.ConversationMessage;
import com.agentsystem.conversation.entity.ConversationShare;
import com.agentsystem.knowledge.service.KnowledgeSourceService;
import com.agentsystem.knowledge.entity.KnowledgeSource;
import com.agentsystem.mcp.service.McpConnectorService;
import com.agentsystem.rag.service.DocumentIngestionService;
import com.agentsystem.schema.AgentRequest;
import com.agentsystem.schema.AgentResponse;
import com.agentsystem.schema.UrlIngestionResult;
import com.agentsystem.user.service.UserPreferenceService;
import com.agentsystem.webfetch.service.WebFetchService;
import com.agentsystem.webfetch.entity.WebFetchWhitelist;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock AgentSystemGraph          agentGraph;
    @Mock DocumentIngestionService ingestionService;
    @Mock McpConnectorService    mcpConnectorService;
    @Mock ConversationService    conversationService;
    @Mock KnowledgeSourceService knowledgeSourceService;
    @Mock WebFetchService        webFetchService;
    @Mock UserPreferenceService  userPreferenceService;
    @Mock LlmProperties          llmProperties;
    @Mock AgentMetrics           agentMetrics;
    @Mock HttpServletRequest     request;
    @InjectMocks AgentController controller;

    private void stubRequest(String email) {
        when(request.getAttribute("authenticatedEmail")).thenReturn(email);
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(request.getAttribute("authenticatedMode")).thenReturn("PERSONAL");
        when(request.getAttribute("authenticatedOrgId")).thenReturn(null);
    }

    // ── listConversations ─────────────────────────────────────────────────────

    @Test
    void listConversations_withEmail_returnsConversations() {
        stubRequest("user@example.com");
        Conversation conv = new Conversation();
        when(conversationService.listConversations(any(OrgContext.class))).thenReturn(List.of(conv));

        ResponseEntity<List<Conversation>> resp = controller.listConversations(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    void listConversations_noEmail_returns401() {
        when(request.getAttribute("authenticatedEmail")).thenReturn(null);
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");

        ResponseEntity<List<Conversation>> resp = controller.listConversations(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    // ── listArchivedConversations ──────────────────────────────────────────────

    @Test
    void listArchivedConversations_noEmail_returns401() {
        when(request.getAttribute("authenticatedEmail")).thenReturn(null);
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");

        ResponseEntity<List<Conversation>> resp = controller.listArchivedConversations(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void listArchivedConversations_withEmail_returns200() {
        stubRequest("user@example.com");
        when(conversationService.listArchivedConversations(any(OrgContext.class))).thenReturn(List.of());

        ResponseEntity<List<Conversation>> resp = controller.listArchivedConversations(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    // ── conversationHistory ────────────────────────────────────────────────────

    @Test
    void conversationHistory_found_returns200() {
        ConversationMessage msg = new ConversationMessage();
        when(conversationService.getMessages("conv-1")).thenReturn(List.of(msg));

        ResponseEntity<List<ConversationMessage>> resp = controller.conversationHistory("conv-1");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void conversationHistory_empty_returns404() {
        when(conversationService.getMessages("conv-1")).thenReturn(List.of());

        ResponseEntity<List<ConversationMessage>> resp = controller.conversationHistory("conv-1");

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── setConversationModel ───────────────────────────────────────────────────

    @Test
    void setConversationModel_success_returns200() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        Conversation conv = new Conversation();
        conv.setId("conv-1");
        conv.setSelectedModel("gpt-4");
        when(conversationService.setConversationModel("conv-1", "owner@example.com", "gpt-4"))
                .thenReturn(conv);

        var resp = controller.setConversationModel("conv-1", Map.of("selectedModel", "gpt-4"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("conversationId", "conv-1");
    }

    @Test
    void setConversationModel_notOwner_returns403() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("other@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(conversationService.setConversationModel(anyString(), anyString(), any()))
                .thenThrow(new SecurityException("not owner"));

        var resp = controller.setConversationModel("conv-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void setConversationModel_notFound_returns404() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(conversationService.setConversationModel(anyString(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("not found"));

        var resp = controller.setConversationModel("conv-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── archiveConversation ────────────────────────────────────────────────────

    @Test
    void archiveConversation_success_returns204() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");

        ResponseEntity<Void> resp = controller.archiveConversation("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void archiveConversation_notOwner_returns403() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("other@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        doThrow(new SecurityException("not owner"))
                .when(conversationService).setArchived("conv-1", "other@example.com", true);

        ResponseEntity<Void> resp = controller.archiveConversation("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void archiveConversation_notFound_returns404() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        doThrow(new IllegalArgumentException("not found"))
                .when(conversationService).setArchived(anyString(), anyString(), anyBoolean());

        ResponseEntity<Void> resp = controller.archiveConversation("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── unarchiveConversation ──────────────────────────────────────────────────

    @Test
    void unarchiveConversation_success_returns204() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");

        ResponseEntity<Void> resp = controller.unarchiveConversation("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void unarchiveConversation_notOwner_returns403() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("other@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        doThrow(new SecurityException("not owner"))
                .when(conversationService).setArchived("conv-1", "other@example.com", false);

        ResponseEntity<Void> resp = controller.unarchiveConversation("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── deleteConversation ─────────────────────────────────────────────────────

    @Test
    void deleteConversation_success_returns204() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");

        ResponseEntity<Void> resp = controller.deleteConversation("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void deleteConversation_notOwner_returns403() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("other@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        doThrow(new SecurityException("not owner"))
                .when(conversationService).deleteConversation("conv-1", "other@example.com");

        ResponseEntity<Void> resp = controller.deleteConversation("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── createShare ────────────────────────────────────────────────────────────

    @Test
    void createShare_success_returns200() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        ConversationShare share = new ConversationShare("conv-1", "tok-abc",
                "owner@example.com", null, "READ_ONLY", "EVERYONE");
        when(conversationService.createShare(anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(share);

        var resp = controller.createShare("conv-1", Map.of("expireDays", 7), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsKey("token");
    }

    @Test
    void createShare_notOwner_returns403() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("other@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(conversationService.createShare(anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenThrow(new SecurityException("not owner"));

        var resp = controller.createShare("conv-1", null, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void createShare_badRequest_returns400() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(conversationService.createShare(anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("bad whitelist"));

        var resp = controller.createShare("conv-1", null, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ── getShare ───────────────────────────────────────────────────────────────

    @Test
    void getShare_found_returns200() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        ConversationShare share = new ConversationShare("conv-1", "tok-abc",
                "owner@example.com", null, "READ_ONLY", "EVERYONE");
        when(conversationService.getShare("conv-1", "owner@example.com")).thenReturn(share);

        var resp = controller.getShare("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void getShare_notFound_returns404() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(conversationService.getShare(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("no share"));

        var resp = controller.getShare("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── revokeShare ────────────────────────────────────────────────────────────

    @Test
    void revokeShare_success_returns204() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");

        ResponseEntity<Void> resp = controller.revokeShare("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void revokeShare_notOwner_returns403() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("other@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        doThrow(new SecurityException("not owner"))
                .when(conversationService).revokeShare("conv-1", "other@example.com");

        ResponseEntity<Void> resp = controller.revokeShare("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void revokeShare_notFound_returns404() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        doThrow(new IllegalArgumentException("no share"))
                .when(conversationService).revokeShare(anyString(), anyString());

        ResponseEntity<Void> resp = controller.revokeShare("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── listKnowledge ──────────────────────────────────────────────────────────

    @Test
    void listKnowledge_returnsAccessibleSources() {
        stubRequest("user@example.com");
        KnowledgeSource ks = new KnowledgeSource("doc.pdf", "doc.pdf", null, 10, "user@example.com");
        when(knowledgeSourceService.listAccessible(any(OrgContext.class))).thenReturn(List.of(ks));

        ResponseEntity<List<KnowledgeSource>> resp = controller.listKnowledge(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    // ── deleteKnowledge ────────────────────────────────────────────────────────

    @Test
    void deleteKnowledge_success_returns204() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");

        var resp = controller.deleteKnowledge("doc.pdf", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void deleteKnowledge_notOwner_returns403() {
        stubRequest("other@example.com");
        doThrow(new SecurityException("not owner"))
                .when(knowledgeSourceService).delete(anyString(), any(OrgContext.class));

        var resp = controller.deleteKnowledge("doc.pdf", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── updateKnowledge ────────────────────────────────────────────────────────

    @Test
    void updateKnowledge_blankSource_returns400() {
        // Returns 400 before reaching service — no request stub needed
        var resp = controller.updateKnowledge(Map.of("source", "  "), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void updateKnowledge_success_returns200() {
        stubRequest("owner@example.com");
        KnowledgeSource updated = new KnowledgeSource("doc.pdf", "New Label", "category", 10, "owner@example.com");
        when(knowledgeSourceService.updateMetadata(eq("doc.pdf"), eq("New Label"), eq("category"), any(OrgContext.class)))
                .thenReturn(updated);

        var resp = controller.updateKnowledge(
                Map.of("source", "doc.pdf", "label", "New Label", "category", "category"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateKnowledge_notOwner_returns403() {
        stubRequest("other@example.com");
        when(knowledgeSourceService.updateMetadata(anyString(), any(), any(), any(OrgContext.class)))
                .thenThrow(new SecurityException("not owner"));

        var resp = controller.updateKnowledge(Map.of("source", "doc.pdf"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── shareKnowledge ─────────────────────────────────────────────────────────

    @Test
    void shareKnowledge_success_returns200() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        KnowledgeSource ks = new KnowledgeSource("doc.pdf", "doc.pdf", null, 10, "owner@example.com");
        when(knowledgeSourceService.updateSharing(anyString(), any(), anyString())).thenReturn(ks);

        var resp = controller.shareKnowledge(
                Map.of("source", "doc.pdf", "emails", List.of("friend@example.com")), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsKey("source");
    }

    @Test
    void shareKnowledge_notOwner_returns403() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("other@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(knowledgeSourceService.updateSharing(anyString(), any(), anyString()))
                .thenThrow(new SecurityException("not owner"));

        var resp = controller.shareKnowledge(Map.of("source", "doc.pdf"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── listWebFetchWhitelist ──────────────────────────────────────────────────

    @Test
    void listWebFetchWhitelist_returnsWhitelist() {
        stubRequest("user@example.com");
        WebFetchWhitelist entry = new WebFetchWhitelist();
        when(webFetchService.listWhitelist(any(OrgContext.class))).thenReturn(List.of(entry));

        var resp = controller.listWebFetchWhitelist(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    // ── addWebFetchDomain ──────────────────────────────────────────────────────

    @Test
    void addWebFetchDomain_blankDomain_returns400() {
        var resp = controller.addWebFetchDomain(Map.of("domain", " "), request);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void addWebFetchDomain_missingDomain_returns400() {
        var resp = controller.addWebFetchDomain(Map.of(), request);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void addWebFetchDomain_success_returns200() {
        stubRequest("user@example.com");
        WebFetchWhitelist entry = new WebFetchWhitelist();
        when(webFetchService.addDomain(eq("example.com"), any(OrgContext.class))).thenReturn(entry);

        var resp = controller.addWebFetchDomain(Map.of("domain", "example.com"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void addWebFetchDomain_duplicate_returns400() {
        stubRequest("user@example.com");
        when(webFetchService.addDomain(anyString(), any(OrgContext.class)))
                .thenThrow(new IllegalArgumentException("already exists"));

        var resp = controller.addWebFetchDomain(Map.of("domain", "example.com"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ── removeWebFetchDomain ───────────────────────────────────────────────────

    @Test
    void removeWebFetchDomain_success_returns204() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");

        ResponseEntity<Void> resp = controller.removeWebFetchDomain("example.com", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void removeWebFetchDomain_notFound_returns404() {
        stubRequest("user@example.com");
        doThrow(new IllegalArgumentException("not found"))
                .when(webFetchService).removeDomain(eq("example.com"), any(OrgContext.class));

        ResponseEntity<Void> resp = controller.removeWebFetchDomain("example.com", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── ingestUrl ─────────────────────────────────────────────────────────────

    @Test
    void ingestUrl_blankUrl_returns400() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");

        var resp = controller.ingestUrl(Map.of("url", " "), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void ingestUrl_missingUrl_returns400() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");

        var resp = controller.ingestUrl(Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void ingestUrl_success_returns200() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        UrlIngestionResult result = new UrlIngestionResult("ingested", "https://example.com", "Example", 3);
        when(mcpConnectorService.fetchAndIngest(anyString(), any(), anyString())).thenReturn(result);

        var resp = controller.ingestUrl(Map.of("url", "https://example.com"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    // ── ingestText ────────────────────────────────────────────────────────────

    @Test
    void ingestText_blankText_returns400() {
        // email not read before the blank-check guard, so no stub needed
        var resp = controller.ingestText(Map.of("text", "  "), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void ingestText_success_returns200() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("test-uuid");
        when(ingestionService.ingestText(anyString(), anyString(), any(), anyBoolean())).thenReturn(5);

        var resp = controller.ingestText(Map.of("text", "Some text to ingest"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("status", "ingested");
        assertThat(resp.getBody()).containsEntry("chunkCount", 5);
    }

    // ── withConversationId via reflection ─────────────────────────────────────

    @Test
    void withConversationId_injectsConversationIdIntoMetadata() throws Exception {
        AgentResponse.RunMetadata meta = new AgentResponse.RunMetadata(
                "run-1", Instant.now(), 100L, 3, "gpt-4", null);
        AgentResponse raw = new AgentResponse(
                "Answer here", List.of(),
                new AgentResponse.RouteDecision("RETRIEVE", "found docs", 0.9),
                false, null, meta);

        AgentResponse result = callWithConversationId(raw, "conv-42");

        assertThat(result.metadata().conversationId()).isEqualTo("conv-42");
    }

    @Test
    void withConversationId_preservesAllOtherFields() throws Exception {
        AgentResponse.RunMetadata meta = new AgentResponse.RunMetadata(
                "run-abc", Instant.now(), 250L, 5, "gpt-4o", null);
        AgentResponse raw = new AgentResponse(
                "The answer", List.of(),
                new AgentResponse.RouteDecision("DIRECT", "no docs needed", 0.8),
                true, "fallback reason", meta);

        AgentResponse result = callWithConversationId(raw, "conv-99");

        assertThat(result.answer()).isEqualTo("The answer");
        assertThat(result.fallbackActivated()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo("fallback reason");
        assertThat(result.metadata().runId()).isEqualTo("run-abc");
        assertThat(result.metadata().durationMs()).isEqualTo(250L);
        assertThat(result.metadata().documentsRetrieved()).isEqualTo(5);
        assertThat(result.metadata().modelUsed()).isEqualTo("gpt-4o");
        assertThat(result.metadata().conversationId()).isEqualTo("conv-99");
    }

    // ── buildErrorResponse via reflection ────────────────────────────────────

    @Test
    void buildErrorResponse_containsErrorInfo() throws Exception {
        RuntimeException ex = new RuntimeException("Graph failed unexpectedly");

        AgentResponse result = callBuildErrorResponse("run-err", "my query", ex, "conv-1");

        assertThat(result.answer()).contains("An internal error occurred");
        assertThat(result.answer()).contains("Graph failed unexpectedly");
        assertThat(result.metadata().runId()).isEqualTo("run-err");
        assertThat(result.metadata().conversationId()).isEqualTo("conv-1");
        assertThat(result.metadata().modelUsed()).isEqualTo("error");
    }

    @Test
    void buildErrorResponse_fallbackActivatedIsTrue() throws Exception {
        Exception ex = new Exception("something went wrong");

        AgentResponse result = callBuildErrorResponse("run-2", "query", ex, "conv-2");

        assertThat(result.fallbackActivated()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo("something went wrong");
        assertThat(result.routeDecision().route()).isEqualTo("ERROR");
    }

    @Test
    void buildErrorResponse_emptySourcesList() throws Exception {
        AgentResponse result = callBuildErrorResponse("r", "q", new RuntimeException("err"), "c");
        assertThat(result.sources()).isEmpty();
    }

    // ── query ─────────────────────────────────────────────────────────────────

    @Test
    void query_graphThrowsException_returns500() {
        stubRequest("user@test.com");

        AgentRequest agentReq = new AgentRequest("What is Java?", null, null, null, false, null, null, null, null);

        when(conversationService.resolveConversation(nullable(String.class), any(OrgContext.class))).thenReturn("conv-1");
        when(conversationService.getConversationModel("conv-1")).thenReturn(null);
        when(userPreferenceService.getSelectedModel("user@test.com")).thenReturn(null);
        when(llmProperties.getDefaultModel()).thenReturn(null);
        when(agentGraph.getGraph()).thenThrow(new RuntimeException("Graph build failed"));

        ResponseEntity<AgentResponse> resp = controller.query(agentReq, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        assertThat(resp.getBody()).isNotNull();
    }

    // ── ingest ────────────────────────────────────────────────────────────────

    @Test
    void ingest_success_returns200() throws Exception {
        stubRequest("user@test.com");

        MultipartFile file = mock(MultipartFile.class);
        Resource resource = mock(Resource.class);
        when(file.getResource()).thenReturn(resource);
        when(file.getOriginalFilename()).thenReturn("test.pdf");
        when(ingestionService.ingest(eq(resource), any(), eq(false))).thenReturn(5);

        ResponseEntity<Map<String, Object>> resp =
                controller.ingest(file, "test-source", "AI", false, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("filename", "test.pdf");
        assertThat(resp.getBody()).containsEntry("chunkCount", 5);
        verify(agentMetrics).recordIngest();
    }

    @Test
    void ingest_noSourceParam_usesFilename() throws Exception {
        stubRequest("user@test.com");

        MultipartFile file = mock(MultipartFile.class);
        Resource resource = mock(Resource.class);
        when(file.getResource()).thenReturn(resource);
        when(file.getOriginalFilename()).thenReturn("document.pdf");
        when(ingestionService.ingest(any(), any(), eq(false))).thenReturn(3);

        ResponseEntity<Map<String, Object>> resp =
                controller.ingest(file, null, null, false, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(knowledgeSourceService).upsert(eq("document.pdf"), eq("document.pdf"), isNull(), eq(3), any(), any());
    }

    // ── ingestText ────────────────────────────────────────────────────────────

    @Test
    void ingestText_emptyText_returns400() {
        ResponseEntity<Map<String, Object>> resp = controller.ingestText(Map.of("text", ""), request);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void ingestText_validText_returns200() {
        stubRequest("user@test.com");
        when(ingestionService.ingestText(anyString(), anyString(), any(), anyBoolean())).thenReturn(4);

        ResponseEntity<Map<String, Object>> resp = controller.ingestText(
                Map.of("text", "This is some text to ingest", "source", "manual-entry"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("status", "ingested");
        assertThat(resp.getBody()).containsEntry("chunkCount", 4);
    }

    // ── ingestUrl ─────────────────────────────────────────────────────────────

    @Test
    void ingestUrl_emptyUrl_returns400() {
        ResponseEntity<UrlIngestionResult> resp = controller.ingestUrl(Map.of("url", ""), request);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void ingestUrl_validUrl_returns200() {
        stubRequest("user@test.com");
        UrlIngestionResult result = new UrlIngestionResult("ok", "https://example.com", "Example Title", 5);
        when(mcpConnectorService.fetchAndIngest("https://example.com", null, "user@test.com")).thenReturn(result);

        ResponseEntity<UrlIngestionResult> resp = controller.ingestUrl(
                Map.of("url", "https://example.com"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().chunkCount()).isEqualTo(5);
    }

    // ── Reflection helpers ────────────────────────────────────────────────────

    private AgentResponse callWithConversationId(AgentResponse raw, String convId) throws Exception {
        Method m = AgentController.class.getDeclaredMethod(
                "withConversationId", AgentResponse.class, String.class);
        m.setAccessible(true);
        return (AgentResponse) m.invoke(controller, raw, convId);
    }

    private AgentResponse callBuildErrorResponse(String runId, String query, Exception ex,
                                                  String convId) throws Exception {
        Method m = AgentController.class.getDeclaredMethod(
                "buildErrorResponse", String.class, String.class, Exception.class, String.class);
        m.setAccessible(true);
        return (AgentResponse) m.invoke(controller, runId, query, ex, convId);
    }
}
