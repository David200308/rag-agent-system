package com.agentsystem.conversation.controller;

import com.agentsystem.conversation.service.ConversationService;
import com.agentsystem.conversation.entity.ConversationMessage;
import com.agentsystem.conversation.entity.ConversationShare;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public endpoint for reading shared conversations.
 *
 * Mounted at /api/v1/share/** which is exempt from AuthFilter.
 */
@RestController
@RequestMapping("/api/v1/share")
@RequiredArgsConstructor
@Tag(name = "Share", description = "Public access to shared conversations")
public class ShareController {

    private final ConversationService conversationService;

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

}
