package com.agentsystem.conversation.service;

import com.agentsystem.conversation.entity.Conversation;
import com.agentsystem.conversation.entity.ConversationMessage;
import com.agentsystem.conversation.entity.ConversationShare;
import com.agentsystem.org.OrgContext;
import com.agentsystem.schema.AgentRequest;

import java.util.List;

public interface ConversationService {

    /**
     * Resolve or create a conversation (org-context aware).
     * Returns the (possibly new) conversationId.
     */
    String resolveConversation(String conversationId, OrgContext ctx);

    /** Resolves userEmail to a user_uuid internally (bridge for callers that only have an email, e.g. the Go scheduler). */
    String resolveConversation(String conversationId, String userEmail);

    /** Save the user's query message. */
    void saveUserMessage(String conversationId, String content);

    /** Save the assistant's answer, linked to the agent runId. */
    void saveAssistantMessage(String conversationId, String content, String runId);

    /**
     * Load all messages for a conversation as {@link AgentRequest.ConversationTurn} objects
     * so they can be passed into the agent pipeline for multi-turn context.
     */
    List<AgentRequest.ConversationTurn> loadHistory(String conversationId);

    /** Return all raw messages for a conversation (for the /history endpoint). */
    List<ConversationMessage> getMessages(String conversationId);

    /** Return non-archived conversations for a user in the correct mode context. */
    List<Conversation> listConversations(OrgContext ctx);

    /** Resolves userEmail to a user_uuid internally (bridge for email-only callers). */
    List<Conversation> listConversations(String userEmail);

    /** Return archived conversations for a user in the correct mode context. */
    List<Conversation> listArchivedConversations(OrgContext ctx);

    /** Resolves userEmail to a user_uuid internally (bridge for email-only callers). */
    List<Conversation> listArchivedConversations(String userEmail);

    /**
     * Set or clear the model for a specific conversation (owner only).
     * Pass null displayName to reset to the user/system default.
     */
    Conversation setConversationModel(String conversationId, String callerUuid, String displayName);

    /** Get the selected model display name for a conversation, or null if none is set. */
    String getConversationModel(String conversationId);

    /**
     * Archive or unarchive a conversation.
     * Only the owner may change archive state.
     */
    Conversation setArchived(String conversationId, String callerUuid, boolean archived);

    /**
     * Delete a conversation and all its messages.
     * Only the owner may delete; throws if the caller is not the owner.
     */
    void deleteConversation(String conversationId, String callerUuid);

    // ── Share link ────────────────────────────────────────────────────────────

    /**
     * Create (or replace) a share link for a conversation.
     *
     * @param conversationId target conversation
     * @param ownerUuid      must be the conversation owner
     * @param expireDays     null → never expires; positive integer → expires in N days
     * @param shareMode      READ_ONLY
     * @param accessType     EVERYONE | WHITELIST
     * @param whitelist      emails when accessType=WHITELIST; only entries that already belong
     *                       to a registered user are resolved and stored (as uuids) — an
     *                       unregistered email is silently dropped, since it can't be granted
     *                       whitelist access without an account
     */
    ConversationShare createShare(String conversationId,
                                  String ownerUuid,
                                  Integer expireDays,
                                  String shareMode,
                                  String accessType,
                                  List<String> whitelist);

    /**
     * Resolve a share token → messages.
     * Returns empty list if token is unknown or expired.
     */
    List<ConversationMessage> getSharedMessages(String token);

    /**
     * Retrieve a share by token without access restriction (for metadata display).
     * Returns the share if active, else throws IllegalArgumentException.
     */
    ConversationShare getShareByToken(String token);

    /**
     * Validate that callerUuid may access the share.
     *
     * Rules:
     *  - EVERYONE  → callerUuid must not be null (login required)
     *  - WHITELIST → callerUuid must appear in the share's whitelist
     *
     * Returns the validated share, or throws SecurityException / IllegalArgumentException.
     */
    ConversationShare validateShareAccess(String token, String callerUuid);

    /**
     * Retrieve the share record for a conversation (for the owner to inspect).
     */
    ConversationShare getShare(String conversationId, String ownerUuid);

    /**
     * Revoke an existing share link.
     * Only the owner may revoke.
     */
    void revokeShare(String conversationId, String ownerUuid);
}
