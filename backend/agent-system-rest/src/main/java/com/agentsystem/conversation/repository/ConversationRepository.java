package com.agentsystem.conversation.repository;

import com.agentsystem.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, String> {

    /** Personal mode (orgId = null). */
    List<Conversation> findByUserEmailAndOrgIdIsNullAndArchivedFalseOrderByUpdatedAtDesc(String userEmail);
    List<Conversation> findByUserEmailAndOrgIdIsNullAndArchivedTrueOrderByUpdatedAtDesc(String userEmail);

    /** Team mode (orgId set). */
    List<Conversation> findByUserEmailAndOrgIdAndArchivedFalseOrderByUpdatedAtDesc(String userEmail, String orgId);
    List<Conversation> findByUserEmailAndOrgIdAndArchivedTrueOrderByUpdatedAtDesc(String userEmail, String orgId);

    /** Backward-compatible aliases (existing callers without org context → personal). */
    default List<Conversation> findByUserEmailAndArchivedFalseOrderByUpdatedAtDesc(String userEmail) {
        return findByUserEmailAndOrgIdIsNullAndArchivedFalseOrderByUpdatedAtDesc(userEmail);
    }
    default List<Conversation> findByUserEmailAndArchivedTrueOrderByUpdatedAtDesc(String userEmail) {
        return findByUserEmailAndOrgIdIsNullAndArchivedTrueOrderByUpdatedAtDesc(userEmail);
    }
}
