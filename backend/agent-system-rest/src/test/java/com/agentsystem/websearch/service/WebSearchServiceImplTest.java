package com.agentsystem.websearch.service;

import com.agentsystem.config.WebSearchProperties;
import com.agentsystem.websearch.service.impl.WebSearchServiceImpl;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises WebSearchServiceImpl against a real, embedded HTTP server standing in for
 * SearXNG — RestClient's builder chain isn't practical to unit-test with mocks, and a
 * throwaway {@link HttpServer} (JDK built-in, no extra test dependency) gives a genuine
 * end-to-end check of the query params sent and the JSON response parsed.
 */
class WebSearchServiceImplTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastRequestUri = new AtomicReference<>();
    private volatile String responseBody = "{\"results\":[]}";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            lastRequestUri.set(exchange.getRequestURI().toString());
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private WebSearchServiceImpl service(boolean enabled, int maxResults) {
        return new WebSearchServiceImpl(new WebSearchProperties(enabled, baseUrl, 5, maxResults));
    }

    @Test
    void search_disabled_throws() {
        WebSearchServiceImpl service = service(false, 5);

        assertThatThrownBy(() -> service.search("query", 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void search_parsesResultsFromJsonResponse() {
        responseBody = """
                {"results":[
                  {"title":"Result One","url":"https://example.com/1","content":"First snippet"},
                  {"title":"Result Two","url":"https://example.com/2","content":"Second snippet"}
                ]}""";

        List<WebSearchResult> results = service(true, 5).search("test query", 5);

        assertThat(results).containsExactly(
                new WebSearchResult("Result One", "https://example.com/1", "First snippet"),
                new WebSearchResult("Result Two", "https://example.com/2", "Second snippet"));
    }

    @Test
    void search_sendsQueryAndJsonFormatAsParams() {
        service(true, 5).search("uric acid", 5);

        assertThat(lastRequestUri.get()).contains("q=uric").contains("format=json");
    }

    @Test
    void search_emptyResults_returnsEmptyList() {
        responseBody = "{\"results\":[]}";

        assertThat(service(true, 5).search("nothing found", 5)).isEmpty();
    }

    @Test
    void search_respectsConfiguredMaxResultsCapEvenWhenCallerAsksForMore() {
        responseBody = """
                {"results":[
                  {"title":"A","url":"https://a.com","content":"a"},
                  {"title":"B","url":"https://b.com","content":"b"},
                  {"title":"C","url":"https://c.com","content":"c"}
                ]}""";

        List<WebSearchResult> results = service(true, 2).search("query", 10);

        assertThat(results).hasSize(2);
    }

    @Test
    void search_missingTitle_defaultsToUntitled() {
        responseBody = "{\"results\":[{\"url\":\"https://example.com\",\"content\":\"snippet\"}]}";

        List<WebSearchResult> results = service(true, 5).search("query", 5);

        assertThat(results.get(0).title()).isEqualTo("(untitled)");
    }
}
