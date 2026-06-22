package com.agentsystem.connector.service;

import java.util.Map;

public interface TelegramService {

    String getBotUsername();

    /**
     * Validate the Telegram Login Widget auth payload and persist the chat_id.
     */
    void validateAndConnect(Map<String, Object> authData, String ownerEmail, String orgId);

    String sendMessage(String ownerEmail, String orgId, String text);

    /**
     * Sends a message to both the conversation owner and the visitor (shared user).
     * Used by the createTelegramGroupSession tool in interactive shared conversations.
     */
    String sendGroupNotification(String ownerEmail, String visitorEmail, String orgId, String content);

    boolean isConnected(String ownerEmail, String orgId);

    void disconnect(String ownerEmail, String orgId);
}
