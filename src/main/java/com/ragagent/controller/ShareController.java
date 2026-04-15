package com.ragagent.controller;

import com.ragagent.conversation.ConversationService;
import com.ragagent.conversation.entity.ConversationMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public (no-auth) endpoint for reading shared conversations.
 *
 * Mounted at /api/v1/share/** which is exempt from AuthFilter.
 */
@RestController
@RequestMapping("/api/v1/share")
@RequiredArgsConstructor
@Tag(name = "Share", description = "Public read access to shared conversations")
public class ShareController {

    private final ConversationService conversationService;

    /**
     * Resolve a share token and return the conversation messages.
     * Returns 404 if the token is unknown or the link has expired.
     */
    @GetMapping("/{token}")
    @Operation(summary = "Read a shared conversation (public, no auth required)")
    public ResponseEntity<List<ConversationMessage>> readShared(@PathVariable String token) {
        try {
            return ResponseEntity.ok(conversationService.getSharedMessages(token));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
