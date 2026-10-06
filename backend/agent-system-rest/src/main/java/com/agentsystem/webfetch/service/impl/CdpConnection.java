package com.agentsystem.webfetch.service.impl;

import com.agentsystem.webfetch.service.WebFetchException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Minimal Chrome DevTools Protocol client over a single WebSocket — just enough
 * to drive one Lightpanda page: request/response correlation by {@code id},
 * flat-session routing via {@code sessionId}, and an event fan-out.
 *
 * One instance per fetch; closing it closes the socket, which makes Lightpanda
 * discard the whole browser context (pages, cookies, storage).
 */
@Slf4j
final class CdpConnection implements WebSocket.Listener, AutoCloseable {

    /** Guards against a hostile page producing an unbounded CDP message (e.g. a giant DOM). */
    private static final int MAX_MESSAGE_CHARS = 16 * 1024 * 1024;

    private final ObjectMapper mapper;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final List<Consumer<JsonNode>> eventListeners = new CopyOnWriteArrayList<>();
    private final StringBuilder frame = new StringBuilder();
    private final Object sendLock = new Object();

    private volatile WebSocket webSocket;

    private CdpConnection(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    static CdpConnection open(HttpClient http, URI endpoint, ObjectMapper mapper, Duration timeout) {
        CdpConnection conn = new CdpConnection(mapper);
        try {
            conn.webSocket = http.newWebSocketBuilder()
                    .connectTimeout(timeout)
                    .buildAsync(endpoint, conn)
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return conn;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebFetchException("Interrupted while connecting to the browser", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new WebFetchException("Browser service is unreachable at " + endpoint + ": "
                    + cause.getClass().getSimpleName()
                    + (cause.getMessage() != null ? " " + cause.getMessage() : ""), cause);
        } catch (TimeoutException e) {
            throw new WebFetchException("Browser service is unreachable at " + endpoint + ": connect timed out", e);
        }
    }

    void onEvent(Consumer<JsonNode> listener) {
        eventListeners.add(listener);
    }

    /** Sends a command and blocks for its result; CDP-level errors become {@link WebFetchException}. */
    JsonNode call(String method, Map<String, ?> params, String sessionId, Duration timeout) {
        int id = nextId.getAndIncrement();
        ObjectNode msg = mapper.createObjectNode();
        msg.put("id", id);
        msg.put("method", method);
        msg.set("params", mapper.valueToTree(params));
        if (sessionId != null) msg.put("sessionId", sessionId);

        CompletableFuture<JsonNode> response = new CompletableFuture<>();
        pending.put(id, response);
        try {
            synchronized (sendLock) {
                webSocket.sendText(msg.toString(), true).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
            JsonNode reply = response.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (reply.has("error")) {
                throw new WebFetchException(method + " failed: " + reply.path("error").path("message").asText());
            }
            return reply.path("result");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebFetchException("Interrupted during " + method, e);
        } catch (TimeoutException e) {
            throw new WebFetchException(method + " timed out", e);
        } catch (ExecutionException e) {
            throw new WebFetchException(method + " failed: " + e.getCause().getMessage(), e.getCause());
        } finally {
            pending.remove(id);
        }
    }

    // ── WebSocket.Listener ─────────────────────────────────────────────────────

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
        frame.append(data);
        if (frame.length() > MAX_MESSAGE_CHARS) {
            failAll(new WebFetchException("Browser response exceeded " + MAX_MESSAGE_CHARS + " chars"));
            ws.abort();
            return null;
        }
        if (last) {
            String text = frame.toString();
            frame.setLength(0);
            dispatch(text);
        }
        ws.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
        failAll(new WebFetchException("Browser closed the connection (" + statusCode + " " + reason + ")"));
        return null;
    }

    @Override
    public void onError(WebSocket ws, Throwable error) {
        failAll(new WebFetchException("Browser connection error: " + error.getMessage(), error));
    }

    private void dispatch(String text) {
        JsonNode msg;
        try {
            msg = mapper.readTree(text);
        } catch (Exception e) {
            log.debug("[CdpConnection] Ignoring unparseable message: {}", e.getMessage());
            return;
        }
        if (msg.has("id")) {
            CompletableFuture<JsonNode> f = pending.get(msg.get("id").asInt());
            if (f != null) f.complete(msg);
        } else if (msg.has("method")) {
            for (Consumer<JsonNode> l : eventListeners) {
                try {
                    l.accept(msg);
                } catch (RuntimeException e) {
                    log.debug("[CdpConnection] Event listener failed: {}", e.getMessage());
                }
            }
        }
    }

    private void failAll(Throwable cause) {
        pending.values().forEach(f -> f.completeExceptionally(cause));
    }

    @Override
    public void close() {
        WebSocket ws = webSocket;
        if (ws == null) return;
        try {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            ws.abort();
        }
    }
}
