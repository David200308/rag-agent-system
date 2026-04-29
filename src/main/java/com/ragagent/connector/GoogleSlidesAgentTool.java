package com.ragagent.connector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Spring AI tool: create a Google Slides presentation.
 * Uses the same ThreadLocal email-injection pattern as {@link GoogleDocsAgentTool}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleSlidesAgentTool {

    private final GoogleSlidesService googleSlidesService;

    private static final ThreadLocal<String> CURRENT_EMAIL = new ThreadLocal<>();

    public void setCurrentEmail(String email) { CURRENT_EMAIL.set(email != null ? email : ""); }
    public void clearCurrentEmail()           { CURRENT_EMAIL.remove(); }

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
        String email = CURRENT_EMAIL.get();
        log.info("[GoogleSlidesAgentTool] Creating presentation '{}' for '{}'", title, email);
        try {
            String url = googleSlidesService.createPresentation(title, content, email);
            return "Presentation created successfully. Open it here: " + url;
        } catch (IllegalStateException e) {
            return "Could not write to Google Slides: " + e.getMessage();
        }
    }
}
