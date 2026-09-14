package com.agentsystem.conversation.controller;

import com.agentsystem.conversation.service.ConversationService;
import com.agentsystem.conversation.entity.ConversationMessage;
import com.agentsystem.conversation.entity.ConversationShare;
import com.agentsystem.user.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
    private final UserAccountService  userAccountService;

    // ── Response record ───────────────────────────────────────────────────────

    public record ShareMetaResponse(
        String shareMode,
        String accessType,
        String ownerEmail,
        String expiresAt,
        List<ConversationMessage> messages
    ) {}

    // ── GET /{token} — read share metadata + messages ──────────────────────────
    // Anonymous access is allowed (EVERYONE shares), but AuthFilter still validates a
    // token when one is presented so WHITELIST shares can check the caller's identity.

    @GetMapping("/{token}")
    @Operation(summary = "Read share metadata and messages (anonymous for EVERYONE shares; "
            + "requires the caller to be on the whitelist for WHITELIST shares)")
    public ResponseEntity<?> readShared(@PathVariable String token, HttpServletRequest httpRequest) {
        String callerUuid = (String) httpRequest.getAttribute("authenticatedUserUuid");
        try {
            ConversationShare share = conversationService.validateShareAccess(token, callerUuid);
            List<ConversationMessage> messages =
                    conversationService.getMessages(share.getConversationId());
            return ResponseEntity.ok(new ShareMetaResponse(
                share.getShareMode(),
                share.getAccessType(),
                userAccountService.getEmailByUuid(share.getOwnerUuid()),
                share.getExpiresAt() != null ? share.getExpiresAt().toString() : null,
                messages
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(java.util.Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

}
