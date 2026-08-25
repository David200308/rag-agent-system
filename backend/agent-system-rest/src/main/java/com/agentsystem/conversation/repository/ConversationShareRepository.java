package com.agentsystem.conversation.repository;

import com.agentsystem.conversation.entity.ConversationShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationShareRepository extends JpaRepository<ConversationShare, Long> {

    Optional<ConversationShare> findByToken(String token);

    Optional<ConversationShare> findByConversationIdAndOwnerUuid(
            String conversationId, String ownerUuid);

    void deleteByConversationId(String conversationId);
}
