package com.agentsystem.connector.tool;

import com.agentsystem.agent.ToolCallBudget;
import com.agentsystem.websearch.service.WebSearchResult;
import com.agentsystem.websearch.service.WebSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSearchAgentToolTest {

    @Mock WebSearchService     webSearchService;
    @Mock ToolCallBudget       toolCallBudget;
    @InjectMocks WebSearchAgentTool tool;

    @BeforeEach
    void setUp() {
        lenient().when(toolCallBudget.tryConsume()).thenReturn(true);
    }

    @Test
    void searchWeb_budgetExhausted_returnsExhaustedMessageWithoutCallingService() {
        when(toolCallBudget.tryConsume()).thenReturn(false);

        String result = tool.searchWeb("query", 5);

        assertThat(result).isEqualTo(ToolCallBudget.EXHAUSTED_MESSAGE);
        verifyNoInteractions(webSearchService);
    }

    @Test
    void searchWeb_noResults_returnsNoResultsMessage() {
        when(webSearchService.search("nothing here", 5)).thenReturn(List.of());

        String result = tool.searchWeb("nothing here", 5);

        assertThat(result).isEqualTo("No web search results found for: nothing here");
    }

    @Test
    void searchWeb_formatsEachResultWithTitleUrlAndSnippet() {
        when(webSearchService.search("uric acid", 5)).thenReturn(List.of(
                new WebSearchResult("Hyperuricemia — Mayo Clinic", "https://mayoclinic.org/uric-acid",
                        "High uric acid levels can cause gout."),
                new WebSearchResult("Uric Acid Test", "https://medlineplus.gov/uric-acid",
                        "A test that measures uric acid in blood.")));

        String result = tool.searchWeb("uric acid", 5);

        assertThat(result)
                .contains("1. Hyperuricemia — Mayo Clinic")
                .contains("https://mayoclinic.org/uric-acid")
                .contains("High uric acid levels can cause gout.")
                .contains("2. Uric Acid Test")
                .contains("https://medlineplus.gov/uric-acid");
    }

    @Test
    void searchWeb_zeroMaxResults_defaultsToFive() {
        when(webSearchService.search(anyString(), anyInt())).thenReturn(List.of());

        tool.searchWeb("query", 0);

        verify(webSearchService).search("query", 5);
    }

    @Test
    void searchWeb_maxResultsAboveTen_isCappedAtTen() {
        when(webSearchService.search(anyString(), anyInt())).thenReturn(List.of());

        tool.searchWeb("query", 50);

        verify(webSearchService).search("query", 10);
    }

    @Test
    void searchWeb_serviceThrows_returnsFriendlyUnavailableMessage() {
        when(webSearchService.search("query", 5))
                .thenThrow(new IllegalStateException("Web search is disabled."));

        String result = tool.searchWeb("query", 5);

        assertThat(result).isEqualTo("Web search is currently unavailable: Web search is disabled.");
    }
}
