package com.agentsystem.connector.tool;

import com.agentsystem.connector.service.GoogleSheetsService;

import com.agentsystem.agent.ToolCallBudget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleSheetsAgentToolTest {

    @Mock GoogleSheetsService     googleSheetsService;
    @Mock ToolCallBudget          toolCallBudget;
    @InjectMocks GoogleSheetsAgentTool tool;

    @BeforeEach
    void setUp() {
        lenient().when(toolCallBudget.tryConsume()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        tool.clearCurrentUserUuid();
    }

    @Test
    void writeToGoogleSheets_budgetExhausted_returnsExhaustedMessageWithoutCallingService() {
        when(toolCallBudget.tryConsume()).thenReturn(false);

        String result = tool.writeToGoogleSheets("Title", "data");

        assertThat(result).isEqualTo(ToolCallBudget.EXHAUSTED_MESSAGE);
        verifyNoInteractions(googleSheetsService);
    }

    // ── ThreadLocal email management ──────────────────────────────────────────

    @Test
    void setCurrentUserUuid_null_setsEmptyString() {
        tool.setCurrentUserUuid(null);
        when(googleSheetsService.createSpreadsheet(anyString(), anyString(), eq(""), isNull()))
                .thenReturn("https://docs.google.com/spreadsheets/d/abc");

        tool.writeToGoogleSheets("Title", "col1,col2");

        verify(googleSheetsService).createSpreadsheet("Title", "col1,col2", "", null);
    }

    @Test
    void clearCurrentUserUuid_removesFromThreadLocal() {
        tool.setCurrentUserUuid("user@example.com");
        tool.clearCurrentUserUuid();

        when(googleSheetsService.createSpreadsheet(anyString(), anyString(), isNull(), isNull()))
                .thenReturn("https://docs.google.com/spreadsheets/d/abc");

        tool.writeToGoogleSheets("Title", "col1,col2");

        verify(googleSheetsService).createSpreadsheet("Title", "col1,col2", null, null);
    }

    // ── writeToGoogleSheets ───────────────────────────────────────────────────

    @Test
    void writeToGoogleSheets_success_returnsUrlMessage() {
        tool.setCurrentUserUuid("user@example.com");
        when(googleSheetsService.createSpreadsheet("Budget", "Name,Amount", "user@example.com", null))
                .thenReturn("https://docs.google.com/spreadsheets/d/xyz");

        String result = tool.writeToGoogleSheets("Budget", "Name,Amount");

        assertThat(result).contains("https://docs.google.com/spreadsheets/d/xyz");
        assertThat(result).contains("successfully");
    }

    @Test
    void writeToGoogleSheets_notConnected_returnsErrorMessage() {
        tool.setCurrentUserUuid("user@example.com");
        when(googleSheetsService.createSpreadsheet(anyString(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("Google Sheets not connected"));

        String result = tool.writeToGoogleSheets("Budget", "data");

        assertThat(result).contains("Could not write to Google Sheets");
        assertThat(result).contains("Google Sheets not connected");
    }

    @Test
    void writeToGoogleSheets_usesCurrentEmailFromThreadLocal() {
        tool.setCurrentUserUuid("sheet-user@example.com");
        when(googleSheetsService.createSpreadsheet(anyString(), anyString(), eq("sheet-user@example.com"), isNull()))
                .thenReturn("https://docs.google.com/spreadsheets/d/new");

        tool.writeToGoogleSheets("My Sheet", "data");

        verify(googleSheetsService).createSpreadsheet("My Sheet", "data", "sheet-user@example.com", null);
    }

    // ── readGoogleSheet ───────────────────────────────────────────────────────

    @Test
    void readGoogleSheet_success_returnsContent() {
        tool.setCurrentUserUuid("user@example.com");
        when(googleSheetsService.readSpreadsheet(
                "https://docs.google.com/spreadsheets/d/abc", "user@example.com", null))
                .thenReturn("Name\tAge\nAlice\t30");

        String result = tool.readGoogleSheet("https://docs.google.com/spreadsheets/d/abc");

        assertThat(result).isEqualTo("Name\tAge\nAlice\t30");
    }

    @Test
    void readGoogleSheet_emptySpreadsheet_returnsEmptyMessage() {
        tool.setCurrentUserUuid("user@example.com");
        when(googleSheetsService.readSpreadsheet(anyString(), anyString(), any())).thenReturn("");

        String result = tool.readGoogleSheet("https://docs.google.com/spreadsheets/d/abc");

        assertThat(result).contains("empty");
    }

    @Test
    void readGoogleSheet_notConnected_returnsErrorMessage() {
        tool.setCurrentUserUuid("user@example.com");
        when(googleSheetsService.readSpreadsheet(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("Google Sheets not connected"));

        String result = tool.readGoogleSheet("https://docs.google.com/spreadsheets/d/abc");

        assertThat(result).contains("Could not read Google Sheet");
        assertThat(result).contains("Google Sheets not connected");
    }

    @Test
    void readGoogleSheet_usesCurrentEmailFromThreadLocal() {
        tool.setCurrentUserUuid("reader@example.com");
        when(googleSheetsService.readSpreadsheet(anyString(), eq("reader@example.com"), isNull()))
                .thenReturn("data");

        tool.readGoogleSheet("https://docs.google.com/spreadsheets/d/abc");

        verify(googleSheetsService).readSpreadsheet(
                "https://docs.google.com/spreadsheets/d/abc", "reader@example.com", null);
    }

    @Test
    void readGoogleSheet_blankContent_returnsEmptyMessage() {
        tool.setCurrentUserUuid("user@example.com");
        when(googleSheetsService.readSpreadsheet(anyString(), anyString(), any())).thenReturn("   ");

        String result = tool.readGoogleSheet("https://docs.google.com/spreadsheets/d/abc");

        assertThat(result).contains("empty");
    }
}
