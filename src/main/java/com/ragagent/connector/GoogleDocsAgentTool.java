package com.ragagent.connector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Spring AI tool: write content to a new Google Docs document.
 *
 * The tool is stateless; the current user's email is injected per-request via
 * {@link #setCurrentEmail(String)} before the ChatClient call and cleared
 * immediately after via {@link #clearCurrentEmail()}.
 *
 * Thread-safety: virtual threads each get their own ThreadLocal storage,
 * so concurrent requests never share state.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleDocsAgentTool {

    private final GoogleDocsService googleDocsService;

    private static final ThreadLocal<String> CURRENT_EMAIL = new ThreadLocal<>();

    public void setCurrentEmail(String email)  { CURRENT_EMAIL.set(email != null ? email : ""); }
    public void clearCurrentEmail()            { CURRENT_EMAIL.remove(); }

    /**
     * Creates a new Google Docs document and writes the given content into it.
     *
     * @param title   the document title (e.g. "Conversation Export – 2025-04-29")
     * @param content the full plain-text body to write
     * @return a confirmation message containing the document URL
     */
    @Tool(description = """
            Write content to a new Google Docs document.
            Use this when the user asks to save, export, or write something to Google Docs.
            Returns the URL of the created document.
            """)
    public String writeToGoogleDocs(String title, String content) {
        String email = CURRENT_EMAIL.get();
        log.info("[GoogleDocsAgentTool] Creating doc '{}' for user '{}'", title, email);
        try {
            String url = googleDocsService.createDocument(title, content, email);
            return "Document created successfully. Open it here: " + url;
        } catch (IllegalStateException e) {
            return "Could not write to Google Docs: " + e.getMessage();
        }
    }
}
