package com.agentsystem.connector.tool;

import com.agentsystem.agent.ToolCallBudget;
import com.agentsystem.websearch.service.WebSearchResult;
import com.agentsystem.websearch.service.WebSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Spring AI tool: real, open-ended web search — distinct from the URL whitelist-based
 * web-fetch feature, which only fetches a specific already-known URL. Backed by a
 * self-hosted SearXNG instance (free, no API key, no query cap).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchAgentTool {

    private final WebSearchService webSearchService;
    private final ToolCallBudget   toolCallBudget;

    @Tool(description = """
            Searches the live web for current information and returns the top results
            (title, URL, snippet) — use this whenever the question needs up-to-date or
            external information that isn't in the knowledge base and the user hasn't
            already given you a specific URL to work with (if they have, use the URL
            directly instead of searching). Ground your answer in the returned snippets
            and cite each source inline as [Source: <url>].
            Specify maxResults (default 5, max 10) to control how many results are returned.
            """)
    public String searchWeb(String query, int maxResults) {
        if (!toolCallBudget.tryConsume()) return ToolCallBudget.EXHAUSTED_MESSAGE;
        int limit = maxResults > 0 ? Math.min(maxResults, 10) : 5;

        try {
            List<WebSearchResult> results = webSearchService.search(query, limit);
            if (results.isEmpty()) {
                return "No web search results found for: " + query;
            }
            log.info("[WebSearchAgentTool] {} result(s) for '{}'", results.size(), query);
            return IntStream.range(0, results.size())
                    .mapToObj(i -> formatResult(i + 1, results.get(i)))
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.warn("[WebSearchAgentTool] Search failed for '{}': {}", query, e.getMessage());
            return "Web search is currently unavailable: " + e.getMessage();
        }
    }

    private String formatResult(int index, WebSearchResult r) {
        return "%d. %s\n   %s\n   %s".formatted(index, r.title(), r.url(), r.snippet());
    }
}
