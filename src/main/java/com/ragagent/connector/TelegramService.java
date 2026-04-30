package com.ragagent.connector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Manages Telegram Bot connections.
 *
 * Connection flow (no OAuth — uses Telegram deep-links):
 *   1. generateAuthUrl(email) → stores a one-time link token, returns t.me deep-link
 *   2. User opens link, sends /start <token> to the bot in Telegram
 *   3. handleWebhook(update) validates the token, stores chat_id in connector_tokens
 *   4. sendMessage(email, text) looks up chat_id and calls sendMessage Bot API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramService {

    private static final String TG_API_BASE = "https://api.telegram.org/bot";

    private final ConnectorProperties           props;
    private final ConnectorTokenRepository      tokenRepo;
    private final ConnectorOAuthStateRepository stateRepo;
    private final RestClient.Builder            restClientBuilder;

    // ── Auth URL (deep-link) ──────────────────────────────────────────────────

    public String generateAuthUrl(String ownerEmail) {
        String linkToken = UUID.randomUUID().toString().replace("-", "");
        stateRepo.save(ConnectorOAuthState.builder()
                .state(linkToken)
                .ownerEmail(ownerEmail != null ? ownerEmail : "")
                .provider("telegram")
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build());

        String botUsername = props.telegram() != null ? props.telegram().botUsername() : "";
        return "https://t.me/" + botUsername + "?start=" + linkToken;
    }

    // ── Webhook handler ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Transactional
    public void handleWebhook(Map<String, Object> update) {
        try {
            Map<String, Object> message = (Map<String, Object>) update.get("message");
            if (message == null) return;

            String text = (String) message.get("text");
            if (text == null || !text.startsWith("/start")) return;

            // /start alone (no token) → ignore
            String[] parts = text.split(" ", 2);
            if (parts.length < 2 || parts[1].isBlank()) return;

            String linkToken = parts[1].trim();
            Map<String, Object> chat = (Map<String, Object>) message.get("chat");
            if (chat == null) return;

            Object rawId = chat.get("id");
            String chatId = rawId instanceof Number n
                    ? String.valueOf(n.longValue())
                    : String.valueOf(rawId);

            ConnectorOAuthState state = stateRepo.findByState(linkToken).orElse(null);

            if (state == null
                    || state.getExpiresAt().isBefore(LocalDateTime.now())
                    || !"telegram".equals(state.getProvider())) {
                sendBotMessage(chatId, "This link is invalid or has expired. Please generate a new connection link from the app.");
                if (state != null) stateRepo.delete(state);
                return;
            }

            String ownerEmail = state.getOwnerEmail();
            stateRepo.delete(state);

            ConnectorToken token = tokenRepo
                    .findByOwnerEmailAndProvider(ownerEmail, "telegram")
                    .orElse(ConnectorToken.builder()
                            .ownerEmail(ownerEmail)
                            .provider("telegram")
                            .build());

            token.setAccessToken(chatId);
            token.setTokenType("telegram");
            tokenRepo.save(token);

            log.info("[TelegramService] Linked chat {} for user {}", chatId, ownerEmail);

            Map<String, Object> from = (Map<String, Object>) message.get("from");
            String firstName = from != null ? (String) from.getOrDefault("first_name", "there") : "there";
            sendBotMessage(chatId, "Hi " + firstName + "! Your Telegram account is now connected. You'll receive messages here from the RAG Agent.");

        } catch (Exception e) {
            log.error("[TelegramService] Error handling webhook", e);
        }
    }

    // ── Send message ──────────────────────────────────────────────────────────

    public String sendMessage(String ownerEmail, String text) {
        String email = ownerEmail != null ? ownerEmail : "";
        ConnectorToken token = tokenRepo.findByOwnerEmailAndProvider(email, "telegram")
                .orElseThrow(() -> new IllegalStateException(
                        "Telegram is not connected. Please connect your Telegram account first."));

        sendBotMessage(token.getAccessToken(), text);
        log.info("[TelegramService] Sent message for user {}", email);
        return "Message sent to your Telegram successfully.";
    }

    // ── Status / disconnect ───────────────────────────────────────────────────

    public boolean isConnected(String ownerEmail) {
        String email = ownerEmail != null ? ownerEmail : "";
        return tokenRepo.findByOwnerEmailAndProvider(email, "telegram").isPresent();
    }

    @Transactional
    public void disconnect(String ownerEmail) {
        String email = ownerEmail != null ? ownerEmail : "";
        tokenRepo.deleteByOwnerEmailAndProvider(email, "telegram");
        log.info("[TelegramService] Disconnected Telegram for {}", email);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void sendBotMessage(String chatId, String text) {
        String botToken = props.telegram() != null ? props.telegram().botToken() : "";
        if (botToken.isBlank()) {
            log.warn("[TelegramService] Bot token not configured");
            return;
        }
        restClientBuilder.build()
                .post()
                .uri(TG_API_BASE + botToken + "/sendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chat_id", chatId, "text", text))
                .retrieve()
                .toBodilessEntity();
    }
}
