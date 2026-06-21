package com.ragagent.controller;

import com.ragagent.conversation.ConversationService;
import com.ragagent.conversation.entity.ConversationMessage;
import com.ragagent.conversation.entity.ConversationShare;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareControllerTest {

    @Mock ConversationService conversationService;
    @InjectMocks ShareController controller;

    private ConversationShare readOnlyShare(String token, String convId) {
        return new ConversationShare(convId, token, "owner@example.com", null,
                "READ_ONLY", "EVERYONE");
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
}
