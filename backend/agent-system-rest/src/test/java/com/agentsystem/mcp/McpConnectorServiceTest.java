package com.ragagent.mcp;

import com.ragagent.knowledge.KnowledgeSourceService;
import com.ragagent.org.OrgContext;
import com.ragagent.rag.DocumentIngestionService;
import com.ragagent.schema.UrlIngestionResult;
import com.ragagent.webfetch.WebFetchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class McpConnectorServiceTest {

    @Mock DocumentIngestionService ingestionService;
    @Mock KnowledgeSourceService   knowledgeSourceService;
    @Mock WebFetchService          webFetchService;
    @Mock RestClient.Builder       restClientBuilder;
    @Mock RestClient               restClient;
    @Mock RestClient.RequestHeadersUriSpec uriSpec;
    @Mock RestClient.RequestHeadersSpec    headersSpec;
    @Mock RestClient.ResponseSpec          responseSpec;

    McpConnectorService service;

    @BeforeEach
    void setUp() {
        service = new McpConnectorService(ingestionService, knowledgeSourceService, webFetchService, restClientBuilder);
        lenient().when(webFetchService.isUrlAllowed(anyString(), any(OrgContext.class))).thenReturn(true);
        lenient().when(restClientBuilder.build()).thenReturn(restClient);
        lenient().when(restClient.get()).thenReturn(uriSpec);
        lenient().when(uriSpec.uri(anyString())).thenReturn(headersSpec);
        lenient().when(headersSpec.header(anyString(), any(String[].class))).thenReturn(headersSpec);
        lenient().when(headersSpec.retrieve()).thenReturn(responseSpec);
    }

    // ── fetchAndIngest — domain whitelist ───────────────────────────────────────

    @Test
    void fetchAndIngest_domainNotWhitelisted_throwsIllegalState() {
        when(webFetchService.isUrlAllowed(anyString(), any(OrgContext.class))).thenReturn(false);

        assertThatThrownBy(() -> service.fetchAndIngest("https://evil.example.com", "tech"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not whitelisted");
    }

    // ── fetchAndIngest — empty/null response ──────────────────────────────────

    @Test
    void fetchAndIngest_nullHtmlResponse_throwsIllegalState() {
        when(responseSpec.body(String.class)).thenReturn(null);

        assertThatThrownBy(() -> service.fetchAndIngest("https://example.com", "tech"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Empty response");
    }

    @Test
    void fetchAndIngest_blankHtmlResponse_throwsIllegalState() {
        when(responseSpec.body(String.class)).thenReturn("   ");

        assertThatThrownBy(() -> service.fetchAndIngest("https://example.com", "tech"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Empty response");
    }

    // ── fetchAndIngest — success ──────────────────────────────────────────────

    @Test
    void fetchAndIngest_success_returnsIngestionResult() {
        String html = "<html><head><title>Test Page</title></head><body>Hello content here</body></html>";
        when(responseSpec.body(String.class)).thenReturn(html);
        when(ingestionService.ingestText(anyString(), anyString(), anyMap(), eq(false))).thenReturn(3);

        UrlIngestionResult result = service.fetchAndIngest("https://example.com", "tech");

        assertThat(result.status()).isEqualTo("ingested");
        assertThat(result.url()).isEqualTo("https://example.com");
        assertThat(result.title()).isEqualTo("Test Page");
        assertThat(result.chunkCount()).isEqualTo(3);
    }

    @Test
    void fetchAndIngest_noCategory_stillIngestsSuccessfully() {
        String html = "<html><head><title>Page</title></head><body>text</body></html>";
        when(responseSpec.body(String.class)).thenReturn(html);
        when(ingestionService.ingestText(anyString(), anyString(), anyMap(), eq(false))).thenReturn(1);

        UrlIngestionResult result = service.fetchAndIngest("https://example.com", null);

        assertThat(result.status()).isEqualTo("ingested");
        assertThat(result.chunkCount()).isEqualTo(1);
    }

    @Test
    void fetchAndIngest_withOwnerEmail_delegatesToThreeArgOverload() {
        String html = "<html><head><title>Doc</title></head><body>body</body></html>";
        when(responseSpec.body(String.class)).thenReturn(html);
        when(ingestionService.ingestText(anyString(), anyString(), anyMap(), eq(false))).thenReturn(2);

        UrlIngestionResult result = service.fetchAndIngest("https://example.com", null, "user@test.com");

        assertThat(result.status()).isEqualTo("ingested");
        assertThat(result.chunkCount()).isEqualTo(2);
    }

    @Test
    void fetchAndIngest_emptyTitle_resultTitleIsEmpty() {
        String html = "<html><head><title></title></head><body>content</body></html>";
        when(responseSpec.body(String.class)).thenReturn(html);
        when(ingestionService.ingestText(anyString(), anyString(), anyMap(), eq(false))).thenReturn(1);

        UrlIngestionResult result = service.fetchAndIngest("https://example.com/path", "news");

        // title in result reflects Jsoup's parsed value; upsert() uses URL as fallback label
        assertThat(result.title()).isEqualTo("");
        assertThat(result.url()).isEqualTo("https://example.com/path");
    }
}
