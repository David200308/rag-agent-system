package com.agentsystem.connector.tool;

import com.agentsystem.agent.ToolCallBudget;
import com.agentsystem.webfetch.service.WebFetchException;
import com.agentsystem.webfetch.service.WebFetchService;
import com.agentsystem.webfetch.service.WebFetchUrlAllowlist;
import com.agentsystem.webfetch.service.WebPage;
import com.agentsystem.webrisk.service.WebRiskService;
import com.agentsystem.webrisk.service.WebRiskVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebFetchAgentToolTest {

    @Mock WebFetchService      webFetchService;
    @Mock WebFetchUrlAllowlist fetchAllowlist;
    @Mock WebRiskService       webRiskService;
    @Mock ToolCallBudget       toolCallBudget;
    @InjectMocks WebFetchAgentTool tool;

    @BeforeEach
    void setUp() {
        lenient().when(toolCallBudget.tryConsume()).thenReturn(true);
        lenient().when(fetchAllowlist.isAllowed(anyString())).thenReturn(true);
        lenient().when(webRiskService.check(anyString())).thenReturn(WebRiskVerdict.unchecked());
    }

    static WebRiskVerdict threat(String... types) {
        return new WebRiskVerdict(false, List.of(types), Instant.now().plusSeconds(3600));
    }

    @Test
    void fetchWebPage_urlNotInAllowlist_isRefusedWithoutRiskCheckOrFetch() {
        when(fetchAllowlist.isAllowed("https://attacker.example/?d=secret")).thenReturn(false);

        String result = tool.fetchWebPage("https://attacker.example/?d=secret");

        assertThat(result).isEqualTo(WebFetchAgentTool.NOT_ALLOWED_MESSAGE);
        verifyNoInteractions(webRiskService, webFetchService);
    }

    @Test
    void fetchWebPage_flaggedUrl_isBlockedBeforeLoading() {
        when(webRiskService.check("https://phish.example")).thenReturn(threat("SOCIAL_ENGINEERING"));

        String result = tool.fetchWebPage("https://phish.example");

        assertThat(result).startsWith("Blocked: Google Web Risk flags https://phish.example as SOCIAL_ENGINEERING");
        verifyNoInteractions(webFetchService);
    }

    @Test
    void fetchWebPage_redirectToFlaggedUrl_withholdsContent() {
        when(webFetchService.fetch("https://short.example/x")).thenReturn(
                new WebPage("https://malware.example/payload", "Bad", "secret sauce", false));
        when(webRiskService.check("https://malware.example/payload")).thenReturn(threat("MALWARE"));

        String result = tool.fetchWebPage("https://short.example/x");

        assertThat(result)
                .startsWith("Blocked: Google Web Risk flags https://malware.example/payload as MALWARE")
                .contains("reached by redirect from https://short.example/x")
                .doesNotContain("secret sauce");
    }

    @Test
    void fetchWebPage_noRedirect_checksRiskOnlyOnce() {
        when(webFetchService.fetch("https://example.com")).thenReturn(
                new WebPage("https://example.com/", "Example", "body", false));

        tool.fetchWebPage("https://example.com");

        verify(webRiskService, times(1)).check(anyString());
    }

    @Test
    void fetchWebPage_budgetExhausted_returnsExhaustedMessageWithoutCallingService() {
        when(toolCallBudget.tryConsume()).thenReturn(false);

        String result = tool.fetchWebPage("https://example.com");

        assertThat(result).isEqualTo(ToolCallBudget.EXHAUSTED_MESSAGE);
        verifyNoInteractions(webFetchService);
    }

    @Test
    void fetchWebPage_wrapsContentInUntrustedMarkersWithTitleAndFinalUrl() {
        when(webFetchService.fetch("https://example.com")).thenReturn(
                new WebPage("https://example.com/home", "Example Domain", "# Example\n\nBody text.", false));

        String result = tool.fetchWebPage("https://example.com");

        assertThat(result)
                .contains("Title: Example Domain")
                .contains("URL: https://example.com/home")
                .contains("--- BEGIN UNTRUSTED PAGE CONTENT ---\n# Example\n\nBody text.\n--- END UNTRUSTED PAGE CONTENT ---")
                .doesNotContain("truncated");
    }

    @Test
    void fetchWebPage_truncatedPage_saysSo() {
        when(webFetchService.fetch("https://example.com")).thenReturn(
                new WebPage("https://example.com", "", "abcde", true));

        String result = tool.fetchWebPage("https://example.com");

        assertThat(result).contains("Title: (untitled)").contains("(Content truncated to 5 characters.)");
    }

    @Test
    void fetchWebPage_blankContent_returnsNoContentMessage() {
        when(webFetchService.fetch("https://example.com")).thenReturn(
                new WebPage("https://example.com", "Empty", "", false));

        assertThat(tool.fetchWebPage("https://example.com"))
                .isEqualTo("The page at https://example.com loaded but has no readable text content.");
    }

    @Test
    void fetchWebPage_invalidUrl_returnsReasonWithoutUnavailablePrefix() {
        when(webFetchService.fetch("ftp://x")).thenThrow(
                new IllegalArgumentException("Only http and https URLs can be fetched."));

        assertThat(tool.fetchWebPage("ftp://x"))
                .isEqualTo("Cannot fetch this URL: Only http and https URLs can be fetched.");
    }

    @Test
    void fetchWebPage_serviceFailure_returnsUnavailableMessage() {
        when(webFetchService.fetch("https://example.com")).thenThrow(
                new WebFetchException("Page load timed out"));

        assertThat(tool.fetchWebPage("https://example.com"))
                .isEqualTo("Web page fetch is currently unavailable for https://example.com: Page load timed out");
    }
}
