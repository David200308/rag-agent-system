package com.ragagent.controller;

import com.ragagent.agent.RagAgentGraph;
import com.ragagent.auth.service.AuthService;
import com.ragagent.conversation.ConversationService;
import com.ragagent.conversation.entity.ConversationMessage;
import com.ragagent.conversation.entity.ConversationShare;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareControllerTest {

    @Mock ConversationService conversationService;
    @Mock AuthService         authService;
    @Mock RagAgentGraph       agentGraph;
    @Mock HttpServletRequest  request;
    @InjectMocks ShareController controller;

    private ConversationShare readOnlyShare(String token, String convId) {
        return new ConversationShare(convId, token, "owner@example.com", null,
                "READ_ONLY", "EVERYONE");
    }

    private ConversationShare interactiveShare(String token, String convId) {
        return new ConversationShare(convId, token, "owner@example.com", null,
                "INTERACTIVE", "EVERYONE");
    }

    // ── readShared ─────────────────────────────────────────────────────────────

    @Test
    void readShared_validToken_returns200WithShareMeta() {
        ConversationShare share = readOnlyShare("tok-abc", "conv-1");
        ConversationMessage msg = new ConversationMessage();
        when(conversationService.getShareByToken("tok-abc")).thenReturn(share);
        when(conversationService.getMessages("conv-1")).thenReturn(List.of(msg));

        ResponseEntity<ShareController.ShareMetaResponse> resp = controller.readShared("tok-abc");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().shareMode()).isEqualTo("READ_ONLY");
        assertThat(resp.getBody().ownerEmail()).isEqualTo("owner@example.com");
        assertThat(resp.getBody().messages()).hasSize(1);
    }

    @Test
    void readShared_unknownToken_returns404() {
        when(conversationService.getShareByToken("bad-token"))
                .thenThrow(new IllegalArgumentException("Share not found"));

        ResponseEntity<ShareController.ShareMetaResponse> resp = controller.readShared("bad-token");

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void readShared_expiredShare_returnsShareInfoAnyway() {
        // Expiry is checked in validateShareAccess, not getShareByToken
        ConversationShare share = readOnlyShare("tok-exp", "conv-2");
        when(conversationService.getShareByToken("tok-exp")).thenReturn(share);
        when(conversationService.getMessages("conv-2")).thenReturn(List.of());

        ResponseEntity<ShareController.ShareMetaResponse> resp = controller.readShared("tok-exp");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    // ── submitSharedQuery — security checks ────────────────────────────────────

    @Test
    void submitSharedQuery_invalidToken_returns404() {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(conversationService.validateShareAccess("bad-token", null))
                .thenThrow(new IllegalArgumentException("not found"));

        ResponseEntity<?> resp = controller.submitSharedQuery("bad-token", Map.of("query", "hello"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void submitSharedQuery_expiredOrUnauthorised_returns401() {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(conversationService.validateShareAccess("tok-1", null))
                .thenThrow(new SecurityException("Access denied"));

        ResponseEntity<?> resp = controller.submitSharedQuery("tok-1", Map.of("query", "hello"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void submitSharedQuery_readOnlyShare_returns403() {
        when(request.getHeader("Authorization")).thenReturn(null);
        ConversationShare share = readOnlyShare("tok-ro", "conv-3");
        when(conversationService.validateShareAccess("tok-ro", null)).thenReturn(share);

        ResponseEntity<?> resp = controller.submitSharedQuery("tok-ro", Map.of("query", "hello"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        assertThat(resp.getBody().toString()).contains("read-only");
    }

    @Test
    void submitSharedQuery_blankQuery_returns400() {
        when(request.getHeader("Authorization")).thenReturn(null);
        ConversationShare share = interactiveShare("tok-int", "conv-4");
        when(conversationService.validateShareAccess("tok-int", null)).thenReturn(share);

        ResponseEntity<?> resp = controller.submitSharedQuery("tok-int", Map.of("query", "  "), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void submitSharedQuery_missingQuery_returns400() {
        when(request.getHeader("Authorization")).thenReturn(null);
        ConversationShare share = interactiveShare("tok-int", "conv-4");
        when(conversationService.validateShareAccess("tok-int", null)).thenReturn(share);

        ResponseEntity<?> resp = controller.submitSharedQuery("tok-int", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void submitSharedQuery_validBearerToken_resolvesEmail() {
        when(request.getHeader("Authorization")).thenReturn("Bearer visitor-jwt");
        when(authService.validateToken("visitor-jwt")).thenReturn("visitor@example.com");
        when(conversationService.validateShareAccess("tok-ro", "visitor@example.com"))
                .thenThrow(new SecurityException("whitelist mismatch"));

        ResponseEntity<?> resp = controller.submitSharedQuery("tok-ro", Map.of("query", "hi"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }
}
