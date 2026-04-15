package com.ragagent.conversation.repository;

import com.ragagent.conversation.entity.ConversationShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationShareRepository extends JpaRepository<ConversationShare, Long> {

    Optional<ConversationShare> findByToken(String token);

    Optional<ConversationShare> findByConversationIdAndOwnerEmail(
            String conversationId, String ownerEmail);

    void deleteByConversationId(String conversationId);
}
