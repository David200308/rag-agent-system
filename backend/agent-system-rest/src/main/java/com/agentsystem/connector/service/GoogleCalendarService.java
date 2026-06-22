package com.agentsystem.connector.service;

public interface GoogleCalendarService {

    /** List upcoming events from the user's primary calendar. */
    String listEvents(String ownerEmail, String orgId, int maxResults);

    /** Create a new event on the user's primary calendar. */
    String createEvent(String ownerEmail, String orgId,
                        String title, String startDateTime, String endDateTime,
                        String description, String location);

    /** Returns true if the user has a valid Google token (shared with Docs/Sheets/Slides). */
    boolean isConnected(String ownerEmail, String orgId);
}
