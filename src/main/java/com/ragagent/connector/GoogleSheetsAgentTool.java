package com.ragagent.connector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Spring AI tool: write tabular data to a new Google Sheets spreadsheet.
 * Uses the same ThreadLocal email-injection pattern as {@link GoogleDocsAgentTool}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleSheetsAgentTool {

    private final GoogleSheetsService googleSheetsService;

    private static final ThreadLocal<String> CURRENT_EMAIL = new ThreadLocal<>();

    public void setCurrentEmail(String email) { CURRENT_EMAIL.set(email != null ? email : ""); }
    public void clearCurrentEmail()           { CURRENT_EMAIL.remove(); }

    /**
     * Creates a new Google Sheets spreadsheet and writes the given data into it.
     *
     * @param title   the spreadsheet title (e.g. "Sales Data – April 2025")
     * @param content tabular data as plain text — rows separated by newlines,
     *                columns separated by tabs or commas. The first row is treated
     *                as a header row.
     * @return a confirmation message containing the spreadsheet URL
     */
    @Tool(description = """
            Write tabular or structured data to a new Google Sheets spreadsheet.
            Use this when the user asks to save, export, or write data/tables to Google Sheets.
            Content should be rows separated by newlines and columns by tabs or commas.
            Returns the URL of the created spreadsheet.
            """)
    public String writeToGoogleSheets(String title, String content) {
        String email = CURRENT_EMAIL.get();
        log.info("[GoogleSheetsAgentTool] Creating sheet '{}' for '{}'", title, email);
        try {
            String url = googleSheetsService.createSpreadsheet(title, content, email);
            return "Spreadsheet created successfully. Open it here: " + url;
        } catch (IllegalStateException e) {
            return "Could not write to Google Sheets: " + e.getMessage();
        }
    }
}
