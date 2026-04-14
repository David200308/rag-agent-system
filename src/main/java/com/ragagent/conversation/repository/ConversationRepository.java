package com.ragagent.conversation.repository;

import com.ragagent.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, String> {

    List<Conversation> findByUserEmailOrderByUpdatedAtDesc(String userEmail);
}
