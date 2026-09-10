package com.agentsystem.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the exact failure this interceptor exists to prevent: OpenRouter/DeepSeek
 * occasionally returning a tool call's function.arguments as a raw JSON object instead
 * of the OpenAI-spec JSON-encoded string, which crashes Spring AI's Jackson binding
 * (OpenAiApi.ChatCompletionMessage.ChatCompletionFunction.arguments is typed String).
 */
class ToolCallArgumentsNormalizingInterceptorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ToolCallArgumentsNormalizingInterceptor interceptor =
            new ToolCallArgumentsNormalizingInterceptor(mapper);

    private ClientHttpResponse fakeResponse(String json, String contentType) throws Exception {
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        HttpHeaders headers = new HttpHeaders();
        if (contentType != null) headers.add("Content-Type", contentType);
        when(response.getHeaders()).thenReturn(headers);
        when(response.getBody()).thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        when(response.getStatusCode()).thenReturn(HttpStatusCode.valueOf(200));
        when(response.getStatusText()).thenReturn("OK");
        return response;
    }

    private ClientHttpRequestExecution executionReturning(ClientHttpResponse response) throws Exception {
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(any(), any())).thenReturn(response);
        return execution;
    }

    private String bodyOf(ClientHttpResponse response) throws Exception {
        return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void intercept_objectArguments_rewrittenToJsonEncodedString() throws Exception {
        String raw = """
                {"choices":[{"message":{"tool_calls":[
                  {"id":"1","type":"function","function":{"name":"searchWeb","arguments":{"query":"OKX","maxResults":5}}}
                ]}}]}""";
        ClientHttpResponse response = fakeResponse(raw, "application/json");
        ClientHttpRequestExecution execution = executionReturning(response);

        ClientHttpResponse result = interceptor.intercept(mock(HttpRequest.class), new byte[0], execution);

        var root = mapper.readTree(bodyOf(result));
        var argumentsNode = root.at("/choices/0/message/tool_calls/0/function/arguments");
        assertThat(argumentsNode.isTextual()).isTrue();
        // The rewritten string must itself parse back to the original object.
        var reparsed = mapper.readTree(argumentsNode.asText());
        assertThat(reparsed.get("query").asText()).isEqualTo("OKX");
        assertThat(reparsed.get("maxResults").asInt()).isEqualTo(5);
    }

    @Test
    void intercept_alreadyStringArguments_leftByteForByteUnchanged() throws Exception {
        String raw = """
                {"choices":[{"message":{"tool_calls":[
                  {"id":"1","type":"function","function":{"name":"searchWeb","arguments":"{\\"query\\":\\"OKX\\"}"}}
                ]}}]}""";
        ClientHttpResponse response = fakeResponse(raw, "application/json");
        ClientHttpRequestExecution execution = executionReturning(response);

        ClientHttpResponse result = interceptor.intercept(mock(HttpRequest.class), new byte[0], execution);

        assertThat(bodyOf(result)).isEqualTo(raw);
    }

    @Test
    void intercept_noToolCalls_leftUnchanged() throws Exception {
        String raw = """
                {"choices":[{"message":{"content":"hello, no tools here"}}]}""";
        ClientHttpResponse response = fakeResponse(raw, "application/json");
        ClientHttpRequestExecution execution = executionReturning(response);

        ClientHttpResponse result = interceptor.intercept(mock(HttpRequest.class), new byte[0], execution);

        assertThat(bodyOf(result)).isEqualTo(raw);
    }

    @Test
    void intercept_nonJsonContentType_passesThroughOriginalResponseUntouched() throws Exception {
        ClientHttpResponse response = fakeResponse("not json at all", "text/plain");
        ClientHttpRequestExecution execution = executionReturning(response);

        ClientHttpResponse result = interceptor.intercept(mock(HttpRequest.class), new byte[0], execution);

        assertThat(result).isSameAs(response);
    }

    @Test
    void intercept_malformedJsonBody_passesThroughOriginalBytes() throws Exception {
        String raw = "{not valid json";
        ClientHttpResponse response = fakeResponse(raw, "application/json");
        ClientHttpRequestExecution execution = executionReturning(response);

        ClientHttpResponse result = interceptor.intercept(mock(HttpRequest.class), new byte[0], execution);

        assertThat(bodyOf(result)).isEqualTo(raw);
    }

    @Test
    void intercept_delegatesStatusAndHeadersFromOriginalResponse() throws Exception {
        ClientHttpResponse response = fakeResponse("{\"choices\":[]}", "application/json");
        ClientHttpRequestExecution execution = executionReturning(response);

        ClientHttpResponse result = interceptor.intercept(mock(HttpRequest.class), new byte[0], execution);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
        assertThat(result.getStatusText()).isEqualTo("OK");
        assertThat(result.getHeaders().getFirst("Content-Type")).isEqualTo("application/json");
    }
}
