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
class GoogleSlidesAgentToolTest {

    @Mock GoogleSlidesService     googleSlidesService;
    @Mock ToolCallBudget          toolCallBudget;
    @InjectMocks GoogleSlidesAgentTool tool;

    @BeforeEach
    void setUp() {
        lenient().when(toolCallBudget.tryConsume()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        tool.clearCurrentEmail();
    }

    @Test
    void writeToGoogleSlides_budgetExhausted_returnsExhaustedMessageWithoutCallingService() {
        when(toolCallBudget.tryConsume()).thenReturn(false);

        String result = tool.writeToGoogleSlides("Title", "content");

        assertThat(result).isEqualTo(ToolCallBudget.EXHAUSTED_MESSAGE);
        verifyNoInteractions(googleSlidesService);
    }

    // ── ThreadLocal email management ──────────────────────────────────────────

    @Test
    void setCurrentEmail_null_setsEmptyString() {
        tool.setCurrentEmail(null);
        when(googleSlidesService.createPresentation(anyString(), anyString(), eq(""), isNull()))
                .thenReturn("https://docs.google.com/presentation/d/abc");

        tool.writeToGoogleSlides("Title", "Slide content");

        verify(googleSlidesService).createPresentation("Title", "Slide content", "", null);
    }

    @Test
    void clearCurrentEmail_removesFromThreadLocal() {
        tool.setCurrentEmail("user@example.com");
        tool.clearCurrentEmail();

        when(googleSlidesService.createPresentation(anyString(), anyString(), isNull(), isNull()))
                .thenReturn("https://docs.google.com/presentation/d/abc");

        tool.writeToGoogleSlides("Title", "Slide content");

        verify(googleSlidesService).createPresentation("Title", "Slide content", null, null);
    }

    // ── writeToGoogleSlides ───────────────────────────────────────────────────

    @Test
    void writeToGoogleSlides_success_returnsUrlMessage() {
        tool.setCurrentEmail("user@example.com");
        when(googleSlidesService.createPresentation("My Deck", "Slide 1\nbody", "user@example.com", null))
                .thenReturn("https://docs.google.com/presentation/d/xyz");

        String result = tool.writeToGoogleSlides("My Deck", "Slide 1\nbody");

        assertThat(result).contains("https://docs.google.com/presentation/d/xyz");
        assertThat(result).contains("successfully");
    }

    @Test
    void writeToGoogleSlides_notConnected_returnsErrorMessage() {
        tool.setCurrentEmail("user@example.com");
        when(googleSlidesService.createPresentation(anyString(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("Google Slides not connected"));

        String result = tool.writeToGoogleSlides("My Deck", "content");

        assertThat(result).contains("Could not write to Google Slides");
        assertThat(result).contains("Google Slides not connected");
    }

    @Test
    void writeToGoogleSlides_usesCurrentEmailFromThreadLocal() {
        tool.setCurrentEmail("presenter@example.com");
        when(googleSlidesService.createPresentation(anyString(), anyString(), eq("presenter@example.com"), isNull()))
                .thenReturn("https://docs.google.com/presentation/d/new");

        tool.writeToGoogleSlides("Deck", "Slide 1\nbody\n---\nSlide 2\nbody2");

        verify(googleSlidesService).createPresentation(
                "Deck", "Slide 1\nbody\n---\nSlide 2\nbody2", "presenter@example.com", null);
    }

    // ── readGoogleSlide ───────────────────────────────────────────────────────

    @Test
    void readGoogleSlide_success_returnsContent() {
        tool.setCurrentEmail("user@example.com");
        when(googleSlidesService.readPresentation(
                "https://docs.google.com/presentation/d/abc", "user@example.com", null))
                .thenReturn("[Slide 1] Title\nBody text");

        String result = tool.readGoogleSlide("https://docs.google.com/presentation/d/abc");

        assertThat(result).isEqualTo("[Slide 1] Title\nBody text");
    }

    @Test
    void readGoogleSlide_emptyPresentation_returnsEmptyMessage() {
        tool.setCurrentEmail("user@example.com");
        when(googleSlidesService.readPresentation(anyString(), anyString(), any())).thenReturn("");

        String result = tool.readGoogleSlide("https://docs.google.com/presentation/d/abc");

        assertThat(result).contains("empty");
    }

    @Test
    void readGoogleSlide_blankContent_returnsEmptyMessage() {
        tool.setCurrentEmail("user@example.com");
        when(googleSlidesService.readPresentation(anyString(), anyString(), any())).thenReturn("  ");

        String result = tool.readGoogleSlide("https://docs.google.com/presentation/d/abc");

        assertThat(result).contains("empty");
    }

    @Test
    void readGoogleSlide_notConnected_returnsErrorMessage() {
        tool.setCurrentEmail("user@example.com");
        when(googleSlidesService.readPresentation(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("Google Slides not connected"));

        String result = tool.readGoogleSlide("https://docs.google.com/presentation/d/abc");

        assertThat(result).contains("Could not read Google Slides");
        assertThat(result).contains("Google Slides not connected");
    }

    @Test
    void readGoogleSlide_usesCurrentEmailFromThreadLocal() {
        tool.setCurrentEmail("viewer@example.com");
        when(googleSlidesService.readPresentation(anyString(), eq("viewer@example.com"), isNull()))
                .thenReturn("slide content");

        tool.readGoogleSlide("https://docs.google.com/presentation/d/abc");

        verify(googleSlidesService).readPresentation(
                "https://docs.google.com/presentation/d/abc", "viewer@example.com", null);
    }
}
