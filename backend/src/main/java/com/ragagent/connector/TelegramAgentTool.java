package com.ragagent.connector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Spring AI tool: send a message to the user's linked Telegram account.
 *
 * Email is injected per-request via setCurrentEmail / clearCurrentEmail
 * (same ThreadLocal pattern used by the Google Workspace tools).
 *
 * In shared interactive conversations, setShareOwnerEmail injects the
 * conversation owner's email so createTelegramGroupSession can notify both parties.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramAgentTool {

    private final TelegramService telegramService;

    private static final ThreadLocal<String> CURRENT_EMAIL     = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ORG_ID   = new ThreadLocal<>();
    private static final ThreadLocal<String> SHARE_OWNER_EMAIL = new ThreadLocal<>();

    public void setCurrentEmail(String email)     { CURRENT_EMAIL.set(email != null ? email : ""); }
    public void clearCurrentEmail()               { CURRENT_EMAIL.remove(); }

    public void setCurrentOrgId(String orgId)     { CURRENT_ORG_ID.set(orgId); }
    public void clearCurrentOrgId()               { CURRENT_ORG_ID.remove(); }

    public void setShareOwnerEmail(String email)  { SHARE_OWNER_EMAIL.set(email); }
    public void clearShareOwnerEmail()            { SHARE_OWNER_EMAIL.remove(); }

    /**
     * Sends a text message to the user's Telegram account.
     *
     * @param message the text to send (plain text, no HTML/Markdown)
     * @return confirmation string or error description
     */
    @Tool(description = """
            Send a text message to the user's Telegram account via the connected Telegram bot.
            Use this when the user asks to send a message to their Telegram, e.g.
            "send this to my Telegram", "message me on Telegram", or "notify me via Telegram".
            The user must have connected their Telegram account first.
            Returns a confirmation that the message was sent.
            """)
    public String sendTelegramMessage(String message) {
        String email = CURRENT_EMAIL.get();
        String orgId = CURRENT_ORG_ID.get();
        log.info("[TelegramAgentTool] Sending Telegram message for user '{}'", email);
        try {
            return telegramService.sendMessage(email, orgId, message);
        } catch (IllegalStateException e) {
            return "Could not send Telegram message: " + e.getMessage();
        }
    }

    /**
     * In a shared interactive conversation, sends the content to both the
     * conversation owner and the current user via Telegram, creating a group
     * notification context. Use when the user asks to "send to Telegram",
     * "create a Telegram group", or "notify both of us on Telegram".
     *
     * @param message the text to send to both parties
     * @return confirmation string describing what was sent
     */
    @Tool(description = """
            In a shared interactive conversation, send content to both the conversation owner
            and the current user's Telegram accounts, creating a shared Telegram group context.
            Use this ONLY when this is a shared conversation AND the user asks to "send to Telegram",
            "create a Telegram group", "notify both of us on Telegram", or similar.
            Both users must have Telegram connected. Falls back to a single message if not in
            a shared context.
            """)
    public String createTelegramGroupSession(String message) {
        String visitorEmail = CURRENT_EMAIL.get();
        String ownerEmail   = SHARE_OWNER_EMAIL.get();
        String orgId        = CURRENT_ORG_ID.get();

        if (ownerEmail == null || ownerEmail.isBlank()) {
            return sendTelegramMessage(message);
        }

        log.info("[TelegramAgentTool] Creating Telegram group session owner='{}' visitor='{}'",
                ownerEmail, visitorEmail);
        try {
            return telegramService.sendGroupNotification(ownerEmail, visitorEmail, orgId, message);
        } catch (Exception e) {
            return "Could not create Telegram group session: " + e.getMessage();
        }
    }
}
