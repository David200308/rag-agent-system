package com.ragagent.conversation;

import com.ragagent.conversation.entity.Conversation;
import com.ragagent.conversation.entity.ConversationMessage;
import com.ragagent.conversation.repository.ConversationMessageRepository;
import com.ragagent.conversation.repository.ConversationRepository;
import com.ragagent.schema.AgentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
