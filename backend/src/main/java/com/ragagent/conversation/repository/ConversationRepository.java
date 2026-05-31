package com.ragagent.conversation.repository;

import com.ragagent.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, String> {

    List<Conversation> findByUserEmailAndArchivedFalseOrderByUpdatedAtDesc(String userEmail);

    List<Conversation> findByUserEmailAndArchivedTrueOrderByUpdatedAtDesc(String userEmail);
}
