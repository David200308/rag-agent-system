package com.ragagent.connector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleSheetsAgentToolTest {

    @Mock GoogleSheetsService     googleSheetsService;
    @InjectMocks GoogleSheetsAgentTool tool;

    @AfterEach
    void tearDown() {
        tool.clearCurrentEmail();
    }

    // ── ThreadLocal email management ──────────────────────────────────────────

    @Test
    void setCurrentEmail_null_setsEmptyString() {
        tool.setCurrentEmail(null);
        when(googleSheetsService.createSpreadsheet(anyString(), anyString(), eq("")))
                .thenReturn("https://docs.google.com/spreadsheets/d/abc");

        tool.writeToGoogleSheets("Title", "col1,col2");

        verify(googleSheetsService).createSpreadsheet("Title", "col1,col2", "");
    }

    @Test
    void clearCurrentEmail_removesFromThreadLocal() {
        tool.setCurrentEmail("user@example.com");
        tool.clearCurrentEmail();

        when(googleSheetsService.createSpreadsheet(anyString(), anyString(), isNull()))
                .thenReturn("https://docs.google.com/spreadsheets/d/abc");

        tool.writeToGoogleSheets("Title", "col1,col2");

        verify(googleSheetsService).createSpreadsheet("Title", "col1,col2", null);
    }

    // ── writeToGoogleSheets ───────────────────────────────────────────────────

    @Test
    void writeToGoogleSheets_success_returnsUrlMessage() {
        tool.setCurrentEmail("user@example.com");
        when(googleSheetsService.createSpreadsheet("Budget", "Name,Amount", "user@example.com"))
                .thenReturn("https://docs.google.com/spreadsheets/d/xyz");

        String result = tool.writeToGoogleSheets("Budget", "Name,Amount");

        assertThat(result).contains("https://docs.google.com/spreadsheets/d/xyz");
        assertThat(result).contains("successfully");
    }

    @Test
    void writeToGoogleSheets_notConnected_returnsErrorMessage() {
        tool.setCurrentEmail("user@example.com");
        when(googleSheetsService.createSpreadsheet(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("Google Sheets not connected"));

        String result = tool.writeToGoogleSheets("Budget", "data");

        assertThat(result).contains("Could not write to Google Sheets");
        assertThat(result).contains("Google Sheets not connected");
    }

    @Test
    void writeToGoogleSheets_usesCurrentEmailFromThreadLocal() {
        tool.setCurrentEmail("sheet-user@example.com");
        when(googleSheetsService.createSpreadsheet(anyString(), anyString(), eq("sheet-user@example.com")))
                .thenReturn("https://docs.google.com/spreadsheets/d/new");

        tool.writeToGoogleSheets("My Sheet", "data");

        verify(googleSheetsService).createSpreadsheet("My Sheet", "data", "sheet-user@example.com");
    }

    // ── readGoogleSheet ───────────────────────────────────────────────────────

    @Test
    void readGoogleSheet_success_returnsContent() {
        tool.setCurrentEmail("user@example.com");
        when(googleSheetsService.readSpreadsheet(
                "https://docs.google.com/spreadsheets/d/abc", "user@example.com"))
                .thenReturn("Name\tAge\nAlice\t30");

        String result = tool.readGoogleSheet("https://docs.google.com/spreadsheets/d/abc");

        assertThat(result).isEqualTo("Name\tAge\nAlice\t30");
    }

    @Test
    void readGoogleSheet_emptySpreadsheet_returnsEmptyMessage() {
        tool.setCurrentEmail("user@example.com");
        when(googleSheetsService.readSpreadsheet(anyString(), anyString())).thenReturn("");

        String result = tool.readGoogleSheet("https://docs.google.com/spreadsheets/d/abc");

        assertThat(result).contains("empty");
    }

    @Test
    void readGoogleSheet_notConnected_returnsErrorMessage() {
        tool.setCurrentEmail("user@example.com");
        when(googleSheetsService.readSpreadsheet(anyString(), anyString()))
                .thenThrow(new IllegalStateException("Google Sheets not connected"));

        String result = tool.readGoogleSheet("https://docs.google.com/spreadsheets/d/abc");

        assertThat(result).contains("Could not read Google Sheet");
        assertThat(result).contains("Google Sheets not connected");
    }

    @Test
    void readGoogleSheet_usesCurrentEmailFromThreadLocal() {
        tool.setCurrentEmail("reader@example.com");
        when(googleSheetsService.readSpreadsheet(anyString(), eq("reader@example.com")))
                .thenReturn("data");

        tool.readGoogleSheet("https://docs.google.com/spreadsheets/d/abc");

        verify(googleSheetsService).readSpreadsheet(
                "https://docs.google.com/spreadsheets/d/abc", "reader@example.com");
    }

    @Test
    void readGoogleSheet_blankContent_returnsEmptyMessage() {
        tool.setCurrentEmail("user@example.com");
        when(googleSheetsService.readSpreadsheet(anyString(), anyString())).thenReturn("   ");

        String result = tool.readGoogleSheet("https://docs.google.com/spreadsheets/d/abc");

        assertThat(result).contains("empty");
    }
}
