package com.agentsystem.connector.service;

public interface GoogleDocsService {

    /**
     * Create a new Google Doc with the given title and plain-text content.
     *
     * @param title      document title
     * @param content    body text (may contain newlines)
     * @param ownerEmail the user whose token to use; {@code ""} when auth is disabled
     * @return the document's browser URL (https://docs.google.com/document/d/{id}/edit)
     */
    String createDocument(String title, String content, String ownerEmail, String orgId);

    /**
     * Reads the plain-text content of an existing Google Doc.
     *
     * @param docUrl     the browser URL or document ID of the Google Doc
     * @param ownerEmail the user whose token to use
     * @return the document title and body text concatenated
     */
    String readDocument(String docUrl, String ownerEmail, String orgId);

    /** Returns true if the user has a valid (possibly refreshable) Google token. */
    boolean isConnected(String ownerEmail, String orgId);
}
