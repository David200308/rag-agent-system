package com.ragagent.conversation;

import com.ragagent.conversation.entity.Conversation;
import com.ragagent.conversation.entity.ConversationMessage;
import com.ragagent.conversation.entity.ConversationShare;
import com.ragagent.conversation.repository.ConversationMessageRepository;
import com.ragagent.conversation.repository.ConversationRepository;
import com.ragagent.conversation.repository.ConversationShareRepository;
import com.ragagent.schema.AgentRequest;
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
public class ConversationService {

    private final ConversationRepository        conversationRepo;
    private final ConversationMessageRepository messageRepo;
    private final ConversationShareRepository   shareRepo;

    /**
     * Resolve or create a conversation.
     * Returns the (possibly new) conversationId.
     */
    @Transactional
    public String resolveConversation(String conversationId, String userEmail) {
        if (conversationId != null && !conversationId.isBlank()) {
            if (conversationRepo.existsById(conversationId)) {
                return conversationId;
            }
            log.warn("[ConversationService] Unknown conversationId={}, creating new one", conversationId);
        }
        String newId = UUID.randomUUID().toString();
        conversationRepo.save(new Conversation(newId, userEmail));
        log.debug("[ConversationService] Created conversation id={}", newId);
        return newId;
    }

    /** Save the user's query message. */
    @Transactional
    public void saveUserMessage(String conversationId, String content) {
        messageRepo.save(new ConversationMessage(conversationId, "user", content, null));
    }

    /** Save the assistant's answer, linked to the agent runId. */
    @Transactional
    public void saveAssistantMessage(String conversationId, String content, String runId) {
        messageRepo.save(new ConversationMessage(conversationId, "assistant", content, runId));
    }

    /**
     * Load all messages for a conversation as {@link AgentRequest.ConversationTurn} objects
     * so they can be passed into the agent pipeline for multi-turn context.
     */
    @Transactional(readOnly = true)
    public List<AgentRequest.ConversationTurn> loadHistory(String conversationId) {
        return messageRepo
                .findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(m -> new AgentRequest.ConversationTurn(m.getRole(), m.getContent()))
                .toList();
    }

    /** Return all raw messages for a conversation (for the /history endpoint). */
    @Transactional(readOnly = true)
    public List<ConversationMessage> getMessages(String conversationId) {
        return messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    /** Return non-archived conversations for a user, newest first. */
    @Transactional(readOnly = true)
    public List<Conversation> listConversations(String userEmail) {
        return conversationRepo.findByUserEmailAndArchivedFalseOrderByUpdatedAtDesc(userEmail);
    }

    /** Return archived conversations for a user, newest first. */
    @Transactional(readOnly = true)
    public List<Conversation> listArchivedConversations(String userEmail) {
        return conversationRepo.findByUserEmailAndArchivedTrueOrderByUpdatedAtDesc(userEmail);
    }

    /**
     * Archive or unarchive a conversation.
     * Only the owner may change archive state.
     */
    @Transactional
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
     */
    @Transactional
    public ConversationShare createShare(String conversationId,
                                         String ownerEmail,
                                         Integer expireDays) {
        Conversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        if (!conv.getUserEmail().equalsIgnoreCase(ownerEmail)) {
            throw new SecurityException("Only the owner can share this conversation.");
        }

        // Replace any existing share for this conversation
        shareRepo.findByConversationIdAndOwnerEmail(conversationId, ownerEmail)
                .ifPresent(existing -> shareRepo.delete(existing));

        Instant expiresAt = expireDays != null
                ? Instant.now().plus(expireDays, ChronoUnit.DAYS)
                : null;

        ConversationShare share = new ConversationShare(
                conversationId, UUID.randomUUID().toString(), ownerEmail, expiresAt);
        shareRepo.save(share);
        log.info("[ConversationService] Share created token={} conversationId={} expiresAt={}",
                share.getToken(), conversationId, expiresAt);
        return share;
    }

    /**
     * Resolve a share token → messages.
     * Returns empty list if token is unknown or expired.
     */
    @Transactional(readOnly = true)
    public List<ConversationMessage> getSharedMessages(String token) {
        return shareRepo.findByToken(token)
                .filter(ConversationShare::isActive)
                .map(s -> messageRepo.findByConversationIdOrderByCreatedAtAsc(s.getConversationId()))
                .orElseThrow(() -> new IllegalArgumentException("Share link not found or expired."));
    }

    /**
     * Retrieve the share record for a conversation (for the owner to inspect).
     */
    @Transactional(readOnly = true)
    public ConversationShare getShare(String conversationId, String ownerEmail) {
        return shareRepo.findByConversationIdAndOwnerEmail(conversationId, ownerEmail)
                .orElseThrow(() -> new IllegalArgumentException("No active share for this conversation."));
    }

    /**
     * Revoke an existing share link.
     * Only the owner may revoke.
     */
    @Transactional
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
