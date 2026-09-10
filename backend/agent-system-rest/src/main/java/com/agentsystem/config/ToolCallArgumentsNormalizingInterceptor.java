package com.agentsystem.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Some OpenAI-compatible providers — observed with deepseek/deepseek-v4-flash-* proxied
 * through OpenRouter, a known category of quirk with non-OpenAI-native models on
 * OpenAI-compatible endpoints — occasionally return a tool call's
 * {@code function.arguments} as a raw JSON object instead of the OpenAI-spec
 * JSON-encoded string. Spring AI's {@code OpenAiApi.ChatCompletionMessage
 * .ChatCompletionFunction} types {@code arguments} as {@code String}, so Jackson throws
 * while deserializing the response before GeneratorNode ever sees it — the whole
 * generation call then fails and falls back to a tool-less, doc-less answer.
 *
 * Registered on every {@code OpenAiApi} RestClient (see ChatModelFactory /
 * LlmProviderConfig) — it only rewrites the response when it actually finds this
 * exact shape, so it's a no-op (and effectively free) for well-formed responses from
 * official OpenAI, compliant DeepSeek-direct, etc.
 */
@Slf4j
public class ToolCallArgumentsNormalizingInterceptor implements ClientHttpRequestInterceptor {

    private final ObjectMapper mapper;

    public ToolCallArgumentsNormalizingInterceptor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        ClientHttpResponse response = execution.execute(request, body);

        String contentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        if (contentType == null || !contentType.contains("json")) {
            return response;
        }

        byte[] original = response.getBody().readAllBytes();
        byte[] normalized = normalize(original);
        return normalized == original ? new BufferedClientHttpResponse(response, original)
                                       : new BufferedClientHttpResponse(response, normalized);
    }

    private byte[] normalize(byte[] original) {
        JsonNode root;
        try {
            root = mapper.readTree(original);
        } catch (IOException e) {
            return original; // not JSON (or empty body) — leave untouched
        }
        if (root == null || !root.path("choices").isArray()) {
            return original;
        }

        boolean changed = false;
        for (JsonNode choice : root.path("choices")) {
            for (JsonNode toolCall : choice.path("message").path("tool_calls")) {
                JsonNode function = toolCall.path("function");
                JsonNode arguments = function.path("arguments");
                if (function.isObject() && !arguments.isMissingNode() && !arguments.isTextual()) {
                    ((ObjectNode) function).put("arguments", arguments.toString());
                    changed = true;
                }
            }
        }
        if (!changed) {
            return original;
        }

        log.warn("[ToolCallArgumentsNormalizingInterceptor] Rewrote non-string function.arguments in LLM response");
        try {
            return mapper.writeValueAsBytes(root);
        } catch (IOException e) {
            log.error("[ToolCallArgumentsNormalizingInterceptor] Failed to re-serialize normalized response, " +
                    "passing original through: {}", e.getMessage());
            return original;
        }
    }

    /** Wraps an existing {@link ClientHttpResponse}, replacing only its (already-buffered) body. */
    private static final class BufferedClientHttpResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final byte[] body;

        BufferedClientHttpResponse(ClientHttpResponse delegate, byte[] body) {
            this.delegate = delegate;
            this.body = body;
        }

        @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
        @Override public HttpStatusCode getStatusCode() throws IOException { return delegate.getStatusCode(); }
        @Override public String getStatusText() throws IOException { return delegate.getStatusText(); }
        @Override public void close() { delegate.close(); }
        @Override public HttpHeaders getHeaders() { return delegate.getHeaders(); }
    }
}
