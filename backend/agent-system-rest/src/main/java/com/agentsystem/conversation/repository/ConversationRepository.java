package com.agentsystem.conversation.repository;

import com.agentsystem.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, String> {

    /** Personal mode (orgId = null). */
    List<Conversation> findByUserUuidAndOrgIdIsNullAndArchivedFalseOrderByUpdatedAtDesc(String userUuid);
    List<Conversation> findByUserUuidAndOrgIdIsNullAndArchivedTrueOrderByUpdatedAtDesc(String userUuid);

    /** Team mode (orgId set). */
    List<Conversation> findByUserUuidAndOrgIdAndArchivedFalseOrderByUpdatedAtDesc(String userUuid, String orgId);
    List<Conversation> findByUserUuidAndOrgIdAndArchivedTrueOrderByUpdatedAtDesc(String userUuid, String orgId);

    /** Backward-compatible aliases (existing callers without org context → personal). */
    default List<Conversation> findByUserUuidAndArchivedFalseOrderByUpdatedAtDesc(String userUuid) {
        return findByUserUuidAndOrgIdIsNullAndArchivedFalseOrderByUpdatedAtDesc(userUuid);
    }
    default List<Conversation> findByUserUuidAndArchivedTrueOrderByUpdatedAtDesc(String userUuid) {
        return findByUserUuidAndOrgIdIsNullAndArchivedTrueOrderByUpdatedAtDesc(userUuid);
    }
}
