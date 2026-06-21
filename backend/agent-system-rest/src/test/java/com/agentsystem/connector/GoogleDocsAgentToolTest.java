package com.agentsystem.connector;

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
class GoogleDocsAgentToolTest {

    @Mock GoogleDocsService     googleDocsService;
    @Mock ToolCallBudget        toolCallBudget;
    @InjectMocks GoogleDocsAgentTool tool;

    @BeforeEach
    void setUp() {
        lenient().when(toolCallBudget.tryConsume()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        tool.clearCurrentEmail();
    }

    @Test
    void writeToGoogleDocs_budgetExhausted_returnsExhaustedMessageWithoutCallingService() {
        when(toolCallBudget.tryConsume()).thenReturn(false);

        String result = tool.writeToGoogleDocs("Title", "Content");

        assertThat(result).isEqualTo(ToolCallBudget.EXHAUSTED_MESSAGE);
        verifyNoInteractions(googleDocsService);
    }

    // ── ThreadLocal email management ──────────────────────────────────────────

    @Test
    void setCurrentEmail_null_setsEmptyString() {
        tool.setCurrentEmail(null);
        // Verify writeToGoogleDocs reads the ThreadLocal (empty email)
        when(googleDocsService.createDocument(anyString(), anyString(), eq(""), isNull()))
                .thenReturn("https://docs.google.com/document/d/abc");

        String result = tool.writeToGoogleDocs("Title", "Content");

        assertThat(result).contains("successfully");
    }

    @Test
    void clearCurrentEmail_removesFromThreadLocal() {
        tool.setCurrentEmail("user@example.com");
        tool.clearCurrentEmail();

        // After clear, ThreadLocal returns null → createDocument called with null
        when(googleDocsService.createDocument(anyString(), anyString(), isNull(), isNull()))
                .thenReturn("https://docs.google.com/document/d/abc");

        tool.writeToGoogleDocs("Title", "Content");

        verify(googleDocsService).createDocument("Title", "Content", null, null);
    }

    // ── writeToGoogleDocs ─────────────────────────────────────────────────────

    @Test
    void writeToGoogleDocs_success_returnsUrlMessage() {
        tool.setCurrentEmail("user@example.com");
        when(googleDocsService.createDocument("My Title", "Body text", "user@example.com", null))
                .thenReturn("https://docs.google.com/document/d/abc123");

        String result = tool.writeToGoogleDocs("My Title", "Body text");

        assertThat(result).contains("https://docs.google.com/document/d/abc123");
        assertThat(result).contains("successfully");
    }

    @Test
    void writeToGoogleDocs_notConnected_returnsErrorMessage() {
        tool.setCurrentEmail("user@example.com");
        when(googleDocsService.createDocument(anyString(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("Google Docs not connected"));

        String result = tool.writeToGoogleDocs("Title", "Content");

        assertThat(result).contains("Could not write to Google Docs");
        assertThat(result).contains("Google Docs not connected");
    }

    // ── readGoogleDoc ─────────────────────────────────────────────────────────

    @Test
    void readGoogleDoc_success_returnsContent() {
        tool.setCurrentEmail("user@example.com");
        when(googleDocsService.readDocument("https://docs.google.com/document/d/abc", "user@example.com", null))
                .thenReturn("Document content here");

        String result = tool.readGoogleDoc("https://docs.google.com/document/d/abc");

        assertThat(result).isEqualTo("Document content here");
    }

    @Test
    void readGoogleDoc_emptyDocument_returnsEmptyMessage() {
        tool.setCurrentEmail("user@example.com");
        when(googleDocsService.readDocument(anyString(), anyString(), any())).thenReturn("");

        String result = tool.readGoogleDoc("https://docs.google.com/document/d/abc");

        assertThat(result).contains("empty");
    }

    @Test
    void readGoogleDoc_notConnected_returnsErrorMessage() {
        tool.setCurrentEmail("user@example.com");
        when(googleDocsService.readDocument(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("Not connected to Google Docs"));

        String result = tool.readGoogleDoc("https://docs.google.com/document/d/abc");

        assertThat(result).contains("Could not read Google Doc");
    }
}
