package com.agentsystem.connector.tool;

import com.agentsystem.connector.service.GoogleSheetsService;

import com.agentsystem.agent.ToolCallBudget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Spring AI tool: write tabular data to a new Google Sheets spreadsheet.
 * Uses the same ThreadLocal user_uuid-injection pattern as {@link GoogleDocsAgentTool}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleSheetsAgentTool {

    private final GoogleSheetsService googleSheetsService;
    private final ToolCallBudget      toolCallBudget;

    private static final ThreadLocal<String> CURRENT_USER_UUID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ORG_ID    = new ThreadLocal<>();

    public void setCurrentUserUuid(String uuid) { CURRENT_USER_UUID.set(uuid != null ? uuid : ""); }
    public void clearCurrentUserUuid()          { CURRENT_USER_UUID.remove(); }

    public void setCurrentOrgId(String orgId)  { CURRENT_ORG_ID.set(orgId); }
    public void clearCurrentOrgId()            { CURRENT_ORG_ID.remove(); }

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
        if (!toolCallBudget.tryConsume()) return ToolCallBudget.EXHAUSTED_MESSAGE;
        String uuid = CURRENT_USER_UUID.get();
        log.info("[GoogleSheetsAgentTool] Creating sheet '{}' for '{}'", title, uuid);
        try {
            String url = googleSheetsService.createSpreadsheet(title, content, uuid, CURRENT_ORG_ID.get());
            return "Spreadsheet created successfully. Open it here: " + url;
        } catch (IllegalStateException e) {
            return "Could not write to Google Sheets: " + e.getMessage();
        }
    }

    @Tool(description = """
            Read the contents of an existing Google Sheets spreadsheet.
            Use this when the user provides a docs.google.com/spreadsheets URL and asks to read,
            summarise, or analyse the spreadsheet data.
            Pass the full URL or spreadsheet ID. Returns all sheet data as tab-separated text.
            """)
    public String readGoogleSheet(String sheetUrl) {
        if (!toolCallBudget.tryConsume()) return ToolCallBudget.EXHAUSTED_MESSAGE;
        String uuid = CURRENT_USER_UUID.get();
        log.info("[GoogleSheetsAgentTool] Reading sheet '{}' for '{}'", sheetUrl, uuid);
        try {
            String content = googleSheetsService.readSpreadsheet(sheetUrl, uuid, CURRENT_ORG_ID.get());
            return content.isBlank() ? "The spreadsheet appears to be empty." : content;
        } catch (IllegalStateException e) {
            return "Could not read Google Sheet: " + e.getMessage();
        }
    }
}
