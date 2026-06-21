package com.agentsystem.connector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Manages Telegram Bot connections via the Telegram Login Widget.
 *
 * Connection flow:
 *   1. Frontend renders the Telegram Login Widget (requires bot username)
 *   2. User clicks "Log in with Telegram" in the widget popup
 *   3. Widget calls window.onTelegramAuth(user) with { id, first_name, hash, auth_date, ... }
 *   4. Frontend POSTs that payload to POST /api/v1/connectors/telegram/connect
 *   5. validateAndConnect() verifies HMAC-SHA256(SHA256(botToken), data_check_string)
 *      and stores the user's chat_id in connector_tokens
 *   6. sendMessage() calls the Bot API sendMessage using the stored chat_id
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramService {

    private static final String TG_API_BASE     = "https://api.telegram.org/bot";
    private static final long   MAX_AUTH_AGE_S  = 86_400; // 24 h

    private final ConnectorProperties      props;
    private final ConnectorTokenRepository tokenRepo;
    private final RestClient.Builder       restClientBuilder;

    // ── Config ────────────────────────────────────────────────────────────────

    public String getBotUsername() {
        return props.telegram() != null ? props.telegram().botUsername() : "";
    }

    // ── Connect (Login Widget callback) ───────────────────────────────────────

    /**
     * Validate the Telegram Login Widget auth payload and persist the chat_id.
     *
     * Telegram validation algorithm:
     *   key       = SHA-256( bot_token )
     *   check_str = sorted( key=value pairs excluding "hash" ) joined by "\n"
     *   expected  = HMAC-SHA256( key, check_str )  — hex-encoded
     *   valid     = expected == authData["hash"]  AND  (now - auth_date) < 86400s
     */
    @Transactional
    public void validateAndConnect(Map<String, Object> authData, String ownerEmail, String orgId) {
        String receivedHash = extract(authData, "hash");
        if (receivedHash == null || receivedHash.isBlank()) {
            throw new IllegalArgumentException("Missing hash in Telegram auth data");
        }

        // Build data_check_string: sorted key=value pairs (exclude hash), joined by \n
        String dataCheckString = authData.entrySet().stream()
                .filter(e -> !"hash".equals(e.getKey()) && e.getValue() != null)
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));

        // Compute expected hash
        String expectedHash = hmacSha256(sha256(botToken()), dataCheckString);

        // Constant-time comparison prevents timing attacks
        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                receivedHash.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Telegram auth hash validation failed");
        }

        // Reject stale auth data
        String authDateStr = extract(authData, "auth_date");
        if (authDateStr != null) {
            long authDate = Long.parseLong(authDateStr);
            if (Instant.now().getEpochSecond() - authDate > MAX_AUTH_AGE_S) {
                throw new IllegalArgumentException("Telegram auth data has expired");
            }
        }

        // Persist chat_id (Telegram user id == chat_id for private chats)
        String chatId = extract(authData, "id");
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalArgumentException("Missing id in Telegram auth data");
        }

        String email = ownerEmail != null ? ownerEmail : "";
        ConnectorToken token = findToken(email, orgId)
                .orElse(ConnectorToken.builder().ownerEmail(email).provider("telegram").orgId(orgId).build());

        token.setAccessToken(chatId);
        token.setTokenType("telegram");
        tokenRepo.save(token);
        log.info("[TelegramService] Connected Telegram chat {} for user {} orgId={}", chatId, email, orgId);
    }

    // ── Send message ──────────────────────────────────────────────────────────

    public String sendMessage(String ownerEmail, String orgId, String text) {
        String email = ownerEmail != null ? ownerEmail : "";
        ConnectorToken token = findToken(email, orgId)
                .orElseThrow(() -> new IllegalStateException(
                        "Telegram is not connected. Please connect your Telegram account first."));

        sendBotMessage(token.getAccessToken(), text);
        log.info("[TelegramService] Sent Telegram message for user {}", email);
        return "Message sent to your Telegram successfully.";
    }

    /**
     * Sends a message to both the conversation owner and the visitor (shared user).
     * Used by the createTelegramGroupSession tool in interactive shared conversations.
     */
    public String sendGroupNotification(String ownerEmail, String visitorEmail, String orgId, String content) {
        StringBuilder result = new StringBuilder();

        // Notify the owner
        try {
            String ownerMsg = (visitorEmail != null && !visitorEmail.isBlank())
                    ? "Message from shared conversation (sent by " + visitorEmail + "):\n\n" + content
                    : "Message from a shared conversation:\n\n" + content;
            sendMessage(ownerEmail, orgId, ownerMsg);
            result.append("Message delivered to the conversation owner via Telegram.");
        } catch (IllegalStateException e) {
            result.append("Conversation owner's Telegram is not connected.");
        }

        // Confirm to the visitor (if distinct from owner and has Telegram)
        if (visitorEmail != null && !visitorEmail.isBlank()
                && !visitorEmail.equalsIgnoreCase(ownerEmail)) {
            try {
                sendMessage(visitorEmail, orgId,
                        "Your message was forwarded to the conversation owner via Telegram:\n\n" + content);
                result.append(" Confirmation also sent to your Telegram.");
            } catch (IllegalStateException ignored) {
                // Visitor doesn't have Telegram connected — owner notification already sent
            }
        }

        log.info("[TelegramService] Group notification sent owner={} visitor={}", ownerEmail, visitorEmail);
        return result.toString().trim();
    }

    // ── Status / disconnect ───────────────────────────────────────────────────

    public boolean isConnected(String ownerEmail, String orgId) {
        String email = ownerEmail != null ? ownerEmail : "";
        return findToken(email, orgId).isPresent();
    }

    @Transactional
    public void disconnect(String ownerEmail, String orgId) {
        String email = ownerEmail != null ? ownerEmail : "";
        if (orgId != null) {
            tokenRepo.deleteByOwnerEmailAndProviderAndOrgId(email, "telegram", orgId);
        } else {
            tokenRepo.deleteByOwnerEmailAndProviderAndOrgIdIsNull(email, "telegram");
        }
        log.info("[TelegramService] Disconnected Telegram for {} orgId={}", email, orgId);
    }

    private Optional<ConnectorToken> findToken(String email, String orgId) {
        return orgId != null
                ? tokenRepo.findByOwnerEmailAndProviderAndOrgId(email, "telegram", orgId)
                : tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull(email, "telegram");
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void sendBotMessage(String chatId, String text) {
        String token = botToken();
        if (token.isBlank()) { log.warn("[TelegramService] Bot token not configured"); return; }
        restClientBuilder.build()
                .post()
                .uri(TG_API_BASE + token + "/sendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chat_id", chatId, "text", text))
                .retrieve()
                .toBodilessEntity();
    }

    private String botToken() {
        return props.telegram() != null && props.telegram().botToken() != null
                ? props.telegram().botToken() : "";
    }

    private static String extract(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
