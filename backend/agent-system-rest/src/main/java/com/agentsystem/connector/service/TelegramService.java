package com.agentsystem.connector.service;

import java.util.Map;

public interface TelegramService {

    String getBotUsername();

    /**
     * Validate the Telegram Login Widget auth payload and persist the chat_id.
     */
    void validateAndConnect(Map<String, Object> authData, String ownerUuid, String orgId);

    String sendMessage(String ownerUuid, String orgId, String text);

    /**
     * Sends a message to both the conversation owner and the visitor (shared user).
     * Used by the createTelegramGroupSession tool in interactive shared conversations.
     */
    String sendGroupNotification(String ownerUuid, String visitorUuid, String orgId, String content);

    boolean isConnected(String ownerUuid, String orgId);

    void disconnect(String ownerUuid, String orgId);
}
