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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramAgentTool {

    private final TelegramService telegramService;

    private static final ThreadLocal<String> CURRENT_EMAIL = new ThreadLocal<>();

    public void setCurrentEmail(String email) { CURRENT_EMAIL.set(email != null ? email : ""); }
    public void clearCurrentEmail()           { CURRENT_EMAIL.remove(); }

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
        log.info("[TelegramAgentTool] Sending Telegram message for user '{}'", email);
        try {
            return telegramService.sendMessage(email, message);
        } catch (IllegalStateException e) {
            return "Could not send Telegram message: " + e.getMessage();
        }
    }
}
