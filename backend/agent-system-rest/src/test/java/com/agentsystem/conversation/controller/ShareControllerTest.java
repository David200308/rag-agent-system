package com.agentsystem.conversation.controller;

import com.agentsystem.conversation.service.ConversationService;
import com.agentsystem.conversation.entity.ConversationMessage;
import com.agentsystem.conversation.entity.ConversationShare;
import com.agentsystem.user.service.UserAccountService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareControllerTest {

    @Mock ConversationService conversationService;
    @Mock UserAccountService  userAccountService;
    @Mock HttpServletRequest  request;
    @InjectMocks ShareController controller;

    private ConversationShare everyoneShare(String token, String convId) {
        lenient().when(userAccountService.getEmailByUuid("owner-uuid")).thenReturn("owner@example.com");
        return new ConversationShare(convId, token, "owner-uuid", null,
                "READ_ONLY", "EVERYONE");
    }

    private ConversationShare whitelistShare(String token, String convId) {
        lenient().when(userAccountService.getEmailByUuid("owner-uuid")).thenReturn("owner@example.com");
        return new ConversationShare(convId, token, "owner-uuid", null,
                "READ_ONLY", "WHITELIST");
    }

    // ── readShared ─────────────────────────────────────────────────────────────

    @Test
    void readShared_everyoneShare_anonymousCaller_returns200WithShareMeta() {
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn(null);
        ConversationShare share = everyoneShare("tok-abc", "conv-1");
        ConversationMessage msg = new ConversationMessage();
        when(conversationService.validateShareAccess("tok-abc", null)).thenReturn(share);
        when(conversationService.getMessages("conv-1")).thenReturn(List.of(msg));

        ResponseEntity<?> resp = controller.readShared("tok-abc", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        var body = (ShareController.ShareMetaResponse) resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.shareMode()).isEqualTo("READ_ONLY");
        assertThat(body.ownerEmail()).isEqualTo("owner@example.com");
        assertThat(body.messages()).hasSize(1);
    }

    @Test
    void readShared_unknownOrExpiredToken_returns404() {
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn(null);
        when(conversationService.validateShareAccess("bad-token", null))
                .thenThrow(new IllegalArgumentException("Share link not found or expired."));

        ResponseEntity<?> resp = controller.readShared("bad-token", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void readShared_whitelistShare_allowedCaller_returns200() {
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("allowed-uuid");
        ConversationShare share = whitelistShare("tok-wl", "conv-2");
        when(conversationService.validateShareAccess("tok-wl", "allowed-uuid")).thenReturn(share);
        when(conversationService.getMessages("conv-2")).thenReturn(List.of());

        ResponseEntity<?> resp = controller.readShared("tok-wl", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void readShared_whitelistShare_anonymousCaller_returns403() {
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn(null);
        when(conversationService.validateShareAccess("tok-wl", null))
                .thenThrow(new SecurityException("Authentication required to access this shared conversation."));

        ResponseEntity<?> resp = controller.readShared("tok-wl", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        assertThat(((Map<?, ?>) resp.getBody()).get("error"))
                .isEqualTo("Authentication required to access this shared conversation.");
    }

    @Test
    void readShared_whitelistShare_notAllowedCaller_returns403() {
        lenient().when(request.getAttribute("authenticatedUserUuid")).thenReturn("stranger-uuid");
        when(conversationService.validateShareAccess("tok-wl", "stranger-uuid"))
                .thenThrow(new SecurityException("Access denied: you are not on the whitelist."));

        ResponseEntity<?> resp = controller.readShared("tok-wl", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }
}
