package com.agentsystem.connector.service;

public interface GoogleSheetsService {

    /**
     * Create a new Google Sheet and populate it with the given data.
     *
     * @param title      spreadsheet title
     * @param content    plain text — rows separated by {@code \n}, columns by tab or comma
     * @param ownerEmail user whose token to use
     * @return the spreadsheet's browser URL
     */
    String createSpreadsheet(String title, String content, String ownerEmail, String orgId);

    /**
     * Read the contents of an existing Google Sheet.
     *
     * @param sheetUrl   the browser URL or spreadsheet ID
     * @param ownerEmail user whose token to use
     * @return all cell values formatted as a tab-separated table
     */
    String readSpreadsheet(String sheetUrl, String ownerEmail, String orgId);

    boolean isConnected(String ownerEmail, String orgId);
}
