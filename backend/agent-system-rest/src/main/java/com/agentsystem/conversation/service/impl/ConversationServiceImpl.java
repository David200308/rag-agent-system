package com.agentsystem.conversation.service.impl;

import com.agentsystem.conversation.service.ConversationService;

import com.agentsystem.conversation.entity.Conversation;
import com.agentsystem.conversation.entity.ConversationMessage;
import com.agentsystem.conversation.entity.ConversationShare;
import com.agentsystem.conversation.repository.ConversationMessageRepository;
import com.agentsystem.conversation.repository.ConversationRepository;
import com.agentsystem.conversation.repository.ConversationShareRepository;
import com.agentsystem.org.OrgContext;
import com.agentsystem.schema.AgentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Persists conversation turns (user queries + assistant answers) to MySQL.
 *
 * Callers supply an optional conversationId:
 *   - absent / blank → a new conversation is created and its UUID returned
 *   - present        → messages are appended to the existing conversation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository        conversationRepo;
    private final ConversationMessageRepository messageRepo;
    private final ConversationShareRepository   shareRepo;

    /**
     * Resolve or create a conversation (org-context aware).
     * Returns the (possibly new) conversationId.
     */
    @Transactional
    @Override
    public String resolveConversation(String conversationId, OrgContext ctx) {
        if (conversationId != null && !conversationId.isBlank()) {
            if (conversationRepo.existsById(conversationId)) {
                return conversationId;
            }
            log.warn("[ConversationService] Unknown conversationId={}, creating new one", conversationId);
        }
        String newId = UUID.randomUUID().toString();
        Conversation conv = new Conversation(newId, ctx != null ? ctx.email() : null);
        if (ctx != null && ctx.isTeam()) conv.setOrgId(ctx.orgId());
        conversationRepo.save(conv);
        log.debug("[ConversationService] Created conversation id={}", newId);
        return newId;
    }

    @Transactional
    @Override
    public String resolveConversation(String conversationId, String userEmail) {
        return resolveConversation(conversationId, new OrgContext(userEmail, "PERSONAL", null));
    }

    /** Save the user's query message. */
    @Transactional
    @Override
    public void saveUserMessage(String conversationId, String content) {
        messageRepo.save(new ConversationMessage(conversationId, "user", content, null));
    }

    /** Save the assistant's answer, linked to the agent runId. */
    @Transactional
    @Override
    public void saveAssistantMessage(String conversationId, String content, String runId) {
        messageRepo.save(new ConversationMessage(conversationId, "assistant", content, runId));
    }

    /**
     * Load all messages for a conversation as {@link AgentRequest.ConversationTurn} objects
     * so they can be passed into the agent pipeline for multi-turn context.
     */
    @Transactional(readOnly = true)
    @Override
    public List<AgentRequest.ConversationTurn> loadHistory(String conversationId) {
        return messageRepo
                .findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(m -> new AgentRequest.ConversationTurn(m.getRole(), m.getContent()))
                .toList();
    }

    /** Return all raw messages for a conversation (for the /history endpoint). */
    @Transactional(readOnly = true)
    @Override
    public List<ConversationMessage> getMessages(String conversationId) {
        return messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    /** Return non-archived conversations for a user in the correct mode context. */
    @Transactional(readOnly = true)
    @Override
    public List<Conversation> listConversations(OrgContext ctx) {
        if (ctx.isTeam()) {
            return conversationRepo.findByUserEmailAndOrgIdAndArchivedFalseOrderByUpdatedAtDesc(
                    ctx.email(), ctx.orgId());
        }
        return conversationRepo.findByUserEmailAndOrgIdIsNullAndArchivedFalseOrderByUpdatedAtDesc(ctx.email());
    }

    @Transactional(readOnly = true)
    @Override
    public List<Conversation> listConversations(String userEmail) {
        return conversationRepo.findByUserEmailAndArchivedFalseOrderByUpdatedAtDesc(userEmail);
    }

    /** Return archived conversations for a user in the correct mode context. */
    @Transactional(readOnly = true)
    @Override
    public List<Conversation> listArchivedConversations(OrgContext ctx) {
        if (ctx.isTeam()) {
            return conversationRepo.findByUserEmailAndOrgIdAndArchivedTrueOrderByUpdatedAtDesc(
                    ctx.email(), ctx.orgId());
        }
        return conversationRepo.findByUserEmailAndOrgIdIsNullAndArchivedTrueOrderByUpdatedAtDesc(ctx.email());
    }

    @Transactional(readOnly = true)
    @Override
    public List<Conversation> listArchivedConversations(String userEmail) {
        return conversationRepo.findByUserEmailAndArchivedTrueOrderByUpdatedAtDesc(userEmail);
    }

    /**
     * Set or clear the model for a specific conversation (owner only).
     * Pass null displayName to reset to the user/system default.
     */
    @Transactional
    @Override
    public Conversation setConversationModel(String conversationId, String callerEmail, String displayName) {
        Conversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));
        if (callerEmail != null && conv.getUserEmail() != null
                && !conv.getUserEmail().equalsIgnoreCase(callerEmail)) {
            throw new SecurityException("Only the owner can change the model for this conversation.");
        }
        conv.setSelectedModel(displayName == null || displayName.isBlank() ? null : displayName);
        return conversationRepo.save(conv);
    }

    /** Get the selected model display name for a conversation, or null if none is set. */
    @Transactional(readOnly = true)
    @Override
    public String getConversationModel(String conversationId) {
        return conversationRepo.findById(conversationId)
                .map(Conversation::getSelectedModel)
                .orElse(null);
    }

    /**
     * Archive or unarchive a conversation.
     * Only the owner may change archive state.
     */
    @Transactional
    @Override
    public Conversation setArchived(String conversationId, String callerEmail, boolean archived) {
        Conversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));
        if (callerEmail != null && conv.getUserEmail() != null
                && !conv.getUserEmail().equalsIgnoreCase(callerEmail)) {
            throw new SecurityException("Only the owner can archive this conversation.");
        }
        conv.setArchived(archived);
        return conversationRepo.save(conv);
    }

    /**
     * Delete a conversation and all its messages.
     * Only the owner may delete; throws if the caller is not the owner.
     */
    @Transactional
    @Override
    public void deleteConversation(String conversationId, String callerEmail) {
        conversationRepo.findById(conversationId).ifPresent(c -> {
            if (callerEmail != null && c.getUserEmail() != null
                    && !c.getUserEmail().equalsIgnoreCase(callerEmail)) {
                throw new SecurityException("Only the owner can delete this conversation.");
            }
            shareRepo.deleteByConversationId(conversationId);
            messageRepo.deleteByConversationId(conversationId);
            conversationRepo.deleteById(conversationId);
            log.info("[ConversationService] Deleted conversation id={} by {}", conversationId, callerEmail);
        });
    }

    // ── Share link ────────────────────────────────────────────────────────────

    /**
     * Create (or replace) a share link for a conversation.
     *
     * @param conversationId target conversation
     * @param ownerEmail     must be the conversation owner
     * @param expireDays     null → never expires; positive integer → expires in N days
     * @param shareMode      READ_ONLY
     * @param accessType     EVERYONE | WHITELIST
     * @param whitelist      required emails when accessType=WHITELIST
     */
    @Transactional
    @Override
    public ConversationShare createShare(String conversationId,
                                         String ownerEmail,
                                         Integer expireDays,
                                         String shareMode,
                                         String accessType,
                                         List<String> whitelist) {
        Conversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        if (!conv.getUserEmail().equalsIgnoreCase(ownerEmail)) {
            throw new SecurityException("Only the owner can share this conversation.");
        }

        // Replace any existing share for this conversation
        shareRepo.findByConversationIdAndOwnerEmail(conversationId, ownerEmail)
                .ifPresent(shareRepo::delete);

        Instant expiresAt = expireDays != null
                ? Instant.now().plus(expireDays, ChronoUnit.DAYS)
                : null;

        ConversationShare share = new ConversationShare(
                conversationId, UUID.randomUUID().toString(), ownerEmail, expiresAt,
                shareMode, accessType);

        if (whitelist != null && !whitelist.isEmpty()) {
            share.getWhitelist().addAll(
                whitelist.stream().map(String::toLowerCase).distinct().toList());
        }

        shareRepo.save(share);
        log.info("[ConversationService] Share created token={} conversationId={} mode={} access={} expiresAt={}",
                share.getToken(), conversationId, shareMode, accessType, expiresAt);
        return share;
    }

    /**
     * Resolve a share token → messages.
     * Returns empty list if token is unknown or expired.
     */
    @Transactional(readOnly = true)
    @Override
    public List<ConversationMessage> getSharedMessages(String token) {
        return shareRepo.findByToken(token)
                .filter(ConversationShare::isActive)
                .map(s -> messageRepo.findByConversationIdOrderByCreatedAtAsc(s.getConversationId()))
                .orElseThrow(() -> new IllegalArgumentException("Share link not found or expired."));
    }

    /**
     * Retrieve a share by token without access restriction (for metadata display).
     * Returns the share if active, else throws IllegalArgumentException.
     */
    @Transactional(readOnly = true)
    @Override
    public ConversationShare getShareByToken(String token) {
        return shareRepo.findByToken(token)
                .filter(ConversationShare::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Share link not found or expired."));
    }

    /**
     * Validate that callerEmail may access the share.
     *
     * Rules:
     *  - EVERYONE  → callerEmail must not be null (login required)
     *  - WHITELIST → callerEmail must appear in the share's whitelist
     *
     * Returns the validated share, or throws SecurityException / IllegalArgumentException.
     */
    @Transactional(readOnly = true)
    @Override
    public ConversationShare validateShareAccess(String token, String callerEmail) {
        ConversationShare share = shareRepo.findByToken(token)
                .filter(ConversationShare::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Share link not found or expired."));

        if ("WHITELIST".equals(share.getAccessType())) {
            if (callerEmail == null || callerEmail.isBlank()) {
                throw new SecurityException("Authentication required to access this shared conversation.");
            }
            boolean allowed = share.getWhitelist().stream()
                    .anyMatch(e -> e.equalsIgnoreCase(callerEmail));
            if (!allowed) {
                throw new SecurityException("Access denied: your email is not on the whitelist.");
            }
        }
        // EVERYONE — anonymous access is allowed

        return share;
    }

    /**
     * Retrieve the share record for a conversation (for the owner to inspect).
     */
    @Transactional(readOnly = true)
    @Override
    public ConversationShare getShare(String conversationId, String ownerEmail) {
        return shareRepo.findByConversationIdAndOwnerEmail(conversationId, ownerEmail)
                .orElseThrow(() -> new IllegalArgumentException("No active share for this conversation."));
    }

    /**
     * Revoke an existing share link.
     * Only the owner may revoke.
     */
    @Transactional
    @Override
    public void revokeShare(String conversationId, String ownerEmail) {
        ConversationShare share = shareRepo
                .findByConversationIdAndOwnerEmail(conversationId, ownerEmail)
                .orElseThrow(() -> new IllegalArgumentException("No share found for this conversation."));

        Conversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found."));
        if (!conv.getUserEmail().equalsIgnoreCase(ownerEmail)) {
            throw new SecurityException("Only the owner can revoke this share.");
        }

        shareRepo.delete(share);
        log.info("[ConversationService] Share revoked conversationId={} by {}", conversationId, ownerEmail);
    }
}
