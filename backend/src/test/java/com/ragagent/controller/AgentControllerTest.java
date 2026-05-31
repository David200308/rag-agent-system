package com.ragagent.controller;

import com.ragagent.agent.RagAgentGraph;
import com.ragagent.config.LlmProperties;
import com.ragagent.conversation.ConversationService;
import com.ragagent.conversation.entity.Conversation;
import com.ragagent.conversation.entity.ConversationMessage;
import com.ragagent.conversation.entity.ConversationShare;
import com.ragagent.knowledge.KnowledgeSourceService;
import com.ragagent.knowledge.entity.KnowledgeSource;
import com.ragagent.mcp.McpConnectorService;
import com.ragagent.rag.DocumentIngestionService;
import com.ragagent.schema.UrlIngestionResult;
import com.ragagent.user.UserPreferenceService;
import com.ragagent.webfetch.WebFetchService;
import com.ragagent.webfetch.entity.WebFetchWhitelist;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock RagAgentGraph          agentGraph;
    @Mock DocumentIngestionService ingestionService;
    @Mock McpConnectorService    mcpConnectorService;
    @Mock ConversationService    conversationService;
    @Mock KnowledgeSourceService knowledgeSourceService;
    @Mock WebFetchService        webFetchService;
    @Mock UserPreferenceService  userPreferenceService;
    @Mock LlmProperties          llmProperties;
    @Mock HttpServletRequest     request;
    @InjectMocks AgentController controller;

    // ── listConversations ─────────────────────────────────────────────────────

    @Test
    void listConversations_withEmail_returnsConversations() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        Conversation conv = new Conversation();
        when(conversationService.listConversations("user@example.com")).thenReturn(List.of(conv));

        ResponseEntity<List<Conversation>> resp = controller.listConversations(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    void listConversations_noEmail_returns401() {
        when(request.getAttribute("authenticatedEmail")).thenReturn(null);

        ResponseEntity<List<Conversation>> resp = controller.listConversations(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    // ── listArchivedConversations ──────────────────────────────────────────────

    @Test
    void listArchivedConversations_noEmail_returns401() {
        when(request.getAttribute("authenticatedEmail")).thenReturn(null);

        ResponseEntity<List<Conversation>> resp = controller.listArchivedConversations(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void listArchivedConversations_withEmail_returns200() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        when(conversationService.listArchivedConversations("user@example.com")).thenReturn(List.of());

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
        when(conversationService.setConversationModel(anyString(), anyString(), any()))
                .thenThrow(new SecurityException("not owner"));

        var resp = controller.setConversationModel("conv-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void setConversationModel_notFound_returns404() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        when(conversationService.setConversationModel(anyString(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("not found"));

        var resp = controller.setConversationModel("conv-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── archiveConversation ────────────────────────────────────────────────────

    @Test
    void archiveConversation_success_returns204() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");

        ResponseEntity<Void> resp = controller.archiveConversation("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void archiveConversation_notOwner_returns403() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("other@example.com");
        doThrow(new SecurityException("not owner"))
                .when(conversationService).setArchived("conv-1", "other@example.com", true);

        ResponseEntity<Void> resp = controller.archiveConversation("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void archiveConversation_notFound_returns404() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        doThrow(new IllegalArgumentException("not found"))
                .when(conversationService).setArchived(anyString(), anyString(), anyBoolean());

        ResponseEntity<Void> resp = controller.archiveConversation("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── unarchiveConversation ──────────────────────────────────────────────────

    @Test
    void unarchiveConversation_success_returns204() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");

        ResponseEntity<Void> resp = controller.unarchiveConversation("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void unarchiveConversation_notOwner_returns403() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("other@example.com");
        doThrow(new SecurityException("not owner"))
                .when(conversationService).setArchived("conv-1", "other@example.com", false);

        ResponseEntity<Void> resp = controller.unarchiveConversation("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── deleteConversation ─────────────────────────────────────────────────────

    @Test
    void deleteConversation_success_returns204() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");

        ResponseEntity<Void> resp = controller.deleteConversation("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void deleteConversation_notOwner_returns403() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("other@example.com");
        doThrow(new SecurityException("not owner"))
                .when(conversationService).deleteConversation("conv-1", "other@example.com");

        ResponseEntity<Void> resp = controller.deleteConversation("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── createShare ────────────────────────────────────────────────────────────

    @Test
    void createShare_success_returns200() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");
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
        when(conversationService.createShare(anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenThrow(new SecurityException("not owner"));

        var resp = controller.createShare("conv-1", null, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void createShare_badRequest_returns400() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");
        when(conversationService.createShare(anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("bad whitelist"));

        var resp = controller.createShare("conv-1", null, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ── getShare ───────────────────────────────────────────────────────────────

    @Test
    void getShare_found_returns200() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");
        ConversationShare share = new ConversationShare("conv-1", "tok-abc",
                "owner@example.com", null, "READ_ONLY", "EVERYONE");
        when(conversationService.getShare("conv-1", "owner@example.com")).thenReturn(share);

        var resp = controller.getShare("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void getShare_notFound_returns404() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        when(conversationService.getShare(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("no share"));

        var resp = controller.getShare("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── revokeShare ────────────────────────────────────────────────────────────

    @Test
    void revokeShare_success_returns204() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");

        ResponseEntity<Void> resp = controller.revokeShare("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void revokeShare_notOwner_returns403() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("other@example.com");
        doThrow(new SecurityException("not owner"))
                .when(conversationService).revokeShare("conv-1", "other@example.com");

        ResponseEntity<Void> resp = controller.revokeShare("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void revokeShare_notFound_returns404() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        doThrow(new IllegalArgumentException("no share"))
                .when(conversationService).revokeShare(anyString(), anyString());

        ResponseEntity<Void> resp = controller.revokeShare("conv-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── listKnowledge ──────────────────────────────────────────────────────────

    @Test
    void listKnowledge_returnsAccessibleSources() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        KnowledgeSource ks = new KnowledgeSource("doc.pdf", "doc.pdf", null, 10, "user@example.com");
        when(knowledgeSourceService.listAccessible("user@example.com")).thenReturn(List.of(ks));

        ResponseEntity<List<KnowledgeSource>> resp = controller.listKnowledge(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    // ── deleteKnowledge ────────────────────────────────────────────────────────

    @Test
    void deleteKnowledge_success_returns204() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");

        var resp = controller.deleteKnowledge("doc.pdf", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void deleteKnowledge_notOwner_returns403() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("other@example.com");
        doThrow(new SecurityException("not owner"))
                .when(knowledgeSourceService).delete("doc.pdf", "other@example.com");

        var resp = controller.deleteKnowledge("doc.pdf", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── updateKnowledge ────────────────────────────────────────────────────────

    @Test
    void updateKnowledge_blankSource_returns400() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");

        var resp = controller.updateKnowledge(Map.of("source", "  "), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void updateKnowledge_success_returns200() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");
        KnowledgeSource updated = new KnowledgeSource("doc.pdf", "New Label", "category", 10, "owner@example.com");
        when(knowledgeSourceService.updateMetadata("doc.pdf", "New Label", "category", "owner@example.com"))
                .thenReturn(updated);

        var resp = controller.updateKnowledge(
                Map.of("source", "doc.pdf", "label", "New Label", "category", "category"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateKnowledge_notOwner_returns403() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("other@example.com");
        when(knowledgeSourceService.updateMetadata(anyString(), any(), any(), anyString()))
                .thenThrow(new SecurityException("not owner"));

        var resp = controller.updateKnowledge(Map.of("source", "doc.pdf"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── shareKnowledge ─────────────────────────────────────────────────────────

    @Test
    void shareKnowledge_success_returns200() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("owner@example.com");
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
        when(knowledgeSourceService.updateSharing(anyString(), any(), anyString()))
                .thenThrow(new SecurityException("not owner"));

        var resp = controller.shareKnowledge(Map.of("source", "doc.pdf"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── listWebFetchWhitelist ──────────────────────────────────────────────────

    @Test
    void listWebFetchWhitelist_returnsWhitelist() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        WebFetchWhitelist entry = new WebFetchWhitelist();
        when(webFetchService.listWhitelist("user@example.com")).thenReturn(List.of(entry));

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
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        WebFetchWhitelist entry = new WebFetchWhitelist();
        when(webFetchService.addDomain("example.com", "user@example.com")).thenReturn(entry);

        var resp = controller.addWebFetchDomain(Map.of("domain", "example.com"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void addWebFetchDomain_duplicate_returns400() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        when(webFetchService.addDomain(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("already exists"));

        var resp = controller.addWebFetchDomain(Map.of("domain", "example.com"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // ── removeWebFetchDomain ───────────────────────────────────────────────────

    @Test
    void removeWebFetchDomain_success_returns204() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");

        ResponseEntity<Void> resp = controller.removeWebFetchDomain("example.com", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void removeWebFetchDomain_notFound_returns404() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
        doThrow(new IllegalArgumentException("not found"))
                .when(webFetchService).removeDomain("example.com", "user@example.com");

        ResponseEntity<Void> resp = controller.removeWebFetchDomain("example.com", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── ingestUrl ─────────────────────────────────────────────────────────────

    @Test
    void ingestUrl_blankUrl_returns400() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");

        var resp = controller.ingestUrl(Map.of("url", " "), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void ingestUrl_missingUrl_returns400() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");

        var resp = controller.ingestUrl(Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void ingestUrl_success_returns200() {
        when(request.getAttribute("authenticatedEmail")).thenReturn("user@example.com");
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
        when(ingestionService.ingestText(anyString(), anyString(), any(), anyBoolean())).thenReturn(5);

        var resp = controller.ingestText(Map.of("text", "Some text to ingest"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("status", "ingested");
        assertThat(resp.getBody()).containsEntry("chunkCount", 5);
    }
}
