package com.agentsystem.connector.service;

public interface GoogleSlidesService {

    /**
     * Create a new Google Slides presentation.
     *
     * @param title      presentation title
     * @param content    text content; slides separated by "---"
     * @param ownerEmail user whose token to use
     * @return the presentation's browser URL
     */
    String createPresentation(String title, String content, String ownerEmail, String orgId);

    /**
     * Read the text content of an existing Google Slides presentation.
     *
     * @param presUrl    the browser URL or presentation ID
     * @param ownerEmail user whose token to use
     * @return slide-by-slide text content
     */
    String readPresentation(String presUrl, String ownerEmail, String orgId);

    boolean isConnected(String ownerEmail, String orgId);
}
