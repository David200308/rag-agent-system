package com.agentsystem.connector.tool;

import com.agentsystem.connector.service.GoogleSlidesService;

import com.agentsystem.agent.ToolCallBudget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Spring AI tool: create a Google Slides presentation.
 * Uses the same ThreadLocal user_uuid-injection pattern as {@link GoogleDocsAgentTool}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleSlidesAgentTool {

    private final GoogleSlidesService googleSlidesService;
    private final ToolCallBudget      toolCallBudget;

    private static final ThreadLocal<String> CURRENT_USER_UUID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ORG_ID    = new ThreadLocal<>();

    public void setCurrentUserUuid(String uuid) { CURRENT_USER_UUID.set(uuid != null ? uuid : ""); }
    public void clearCurrentUserUuid()          { CURRENT_USER_UUID.remove(); }

    public void setCurrentOrgId(String orgId)  { CURRENT_ORG_ID.set(orgId); }
    public void clearCurrentOrgId()            { CURRENT_ORG_ID.remove(); }

    /**
     * Creates a new Google Slides presentation with the given content.
     *
     * @param title   the presentation title
     * @param content slide content — slides separated by "---" on its own line.
     *                The first line of each slide is the slide title;
     *                remaining lines become the body text.
     * @return a confirmation message containing the presentation URL
     */
    @Tool(description = """
            Create a new Google Slides presentation and populate it with content.
            Use this when the user asks to create a presentation or write slides in Google Slides.
            Separate slides with "---" on its own line. The first line of each block is the slide
            title; remaining lines become the body. Returns the URL of the created presentation.
            """)
    public String writeToGoogleSlides(String title, String content) {
        if (!toolCallBudget.tryConsume()) return ToolCallBudget.EXHAUSTED_MESSAGE;
        String uuid = CURRENT_USER_UUID.get();
        log.info("[GoogleSlidesAgentTool] Creating presentation '{}' for '{}'", title, uuid);
        try {
            String url = googleSlidesService.createPresentation(title, content, uuid, CURRENT_ORG_ID.get());
            return "Presentation created successfully. Open it here: " + url;
        } catch (IllegalStateException e) {
            return "Could not write to Google Slides: " + e.getMessage();
        }
    }

    @Tool(description = """
            Read the text content of an existing Google Slides presentation.
            Use this when the user provides a docs.google.com/presentation URL and asks to read,
            summarise, or use the content of that presentation.
            Pass the full URL or presentation ID. Returns slide-by-slide text content.
            """)
    public String readGoogleSlide(String presUrl) {
        if (!toolCallBudget.tryConsume()) return ToolCallBudget.EXHAUSTED_MESSAGE;
        String uuid = CURRENT_USER_UUID.get();
        log.info("[GoogleSlidesAgentTool] Reading presentation '{}' for '{}'", presUrl, uuid);
        try {
            String content = googleSlidesService.readPresentation(presUrl, uuid, CURRENT_ORG_ID.get());
            return content.isBlank() ? "The presentation appears to be empty." : content;
        } catch (IllegalStateException e) {
            return "Could not read Google Slides: " + e.getMessage();
        }
    }
}
