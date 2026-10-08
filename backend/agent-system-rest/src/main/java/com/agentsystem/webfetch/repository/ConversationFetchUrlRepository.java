package com.agentsystem.webfetch.repository;

import com.agentsystem.webfetch.entity.ConversationFetchUrl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ConversationFetchUrlRepository extends JpaRepository<ConversationFetchUrl, Long> {

    @Query("SELECT u.urlHash FROM ConversationFetchUrl u WHERE u.conversationId = :conversationId")
    List<String> findUrlHashesByConversationId(String conversationId);
}
