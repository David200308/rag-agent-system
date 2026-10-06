package com.agentsystem.webfetch.service.impl;

import com.agentsystem.config.WebFetchProperties;
import com.agentsystem.webfetch.service.WebFetchException;
import com.agentsystem.webfetch.service.WebFetchService;
import com.agentsystem.webfetch.service.WebPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Fetches pages through a self-hosted Lightpanda headless browser (see
 * docker-compose.yml's {@code lightpanda} service) over CDP. JavaScript runs, so
 * client-rendered pages come back with their real content; the result is taken
 * from Lightpanda's native {@code LP.getMarkdown} dump.
 *
 * SSRF: Lightpanda is started with {@code --block-private-networks}, which rejects
 * private/loopback/link-local IPs when each socket opens — covering redirects, JS
 * sub-requests and DNS rebinding, none of which a URL pre-check here could see.
 * This service therefore only validates the URL's shape.
 *
 * Lightpanda rejects WebSocket handshakes whose Host header isn't localhost, an IP
 * literal, or its --advertise-host name (DNS-rebinding guard). Releases before the
 * advertise-host fix reject the compose service name outright, so the CDP host is
 * resolved here and dialed as an IP literal — accepted by every version.
 */
@Slf4j
@Service
public class LightpandaWebFetchService implements WebFetchService {

    private final WebFetchProperties props;
    private final ObjectMapper       mapper;
    private final HttpClient         httpClient;
    private final Semaphore          permits;

    public LightpandaWebFetchService(WebFetchProperties props, ObjectMapper mapper) {
        this.props      = props;
        this.mapper     = mapper;
        this.httpClient = HttpClient.newHttpClient();
        this.permits    = new Semaphore(props.maxConcurrent());
    }

    @Override
    public WebPage fetch(String url) {
        if (!props.enabled()) {
            throw new IllegalStateException("Web fetch is disabled.");
        }
        URI target = WebFetchService.parseUrl(url);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(props.timeoutSeconds());
        try {
            if (!permits.tryAcquire(props.timeoutSeconds(), TimeUnit.SECONDS)) {
                throw new IllegalStateException("Web fetch is busy, try again shortly.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebFetchException("Interrupted while waiting for the browser", e);
        }

        log.info("[WebFetchService] Fetching {}", target);
        try (CdpConnection cdp = CdpConnection.open(
                httpClient, toIpLiteralEndpoint(props.cdpUrl()), mapper, remaining(deadline))) {
            return load(cdp, target, deadline);
        } finally {
            permits.release();
        }
    }

    /** {@code ws://lightpanda:9222} → {@code ws://172.18.0.5:9222}; resolved per fetch so container restarts are picked up. */
    static URI toIpLiteralEndpoint(String cdpUrl) {
        URI configured = URI.create(cdpUrl);
        try {
            InetAddress addr = InetAddress.getByName(configured.getHost());
            String ip = addr.getHostAddress();
            if (addr instanceof Inet6Address) {
                int zone = ip.indexOf('%');
                ip = "[" + (zone >= 0 ? ip.substring(0, zone) : ip) + "]";
            }
            return new URI(configured.getScheme(), null, ip, configured.getPort(),
                    configured.getPath(), configured.getQuery(), null);
        } catch (UnknownHostException e) {
            throw new WebFetchException("Browser service host '" + configured.getHost() + "' does not resolve", e);
        } catch (URISyntaxException e) {
            throw new WebFetchException("Invalid web-fetch.cdp-url: " + cdpUrl, e);
        }
    }

    private WebPage load(CdpConnection cdp, URI target, long deadline) {
        String targetId = cdp.call("Target.createTarget", Map.of("url", "about:blank"), null, remaining(deadline))
                .path("targetId").asText();
        String sessionId = cdp.call("Target.attachToTarget", Map.of("targetId", targetId, "flatten", true),
                        null, remaining(deadline))
                .path("sessionId").asText();

        cdp.call("Page.enable", Map.of(), sessionId, remaining(deadline));
        // Enabling lifecycle events replays about:blank's own "load" before this call
        // returns, so listeners registered afterwards only see the real navigation.
        cdp.call("Page.setLifecycleEventsEnabled", Map.of("enabled", true), sessionId, remaining(deadline));

        CompletableFuture<Void> loaded      = new CompletableFuture<>();
        CompletableFuture<Void> networkIdle = new CompletableFuture<>();
        cdp.onEvent(event -> {
            if (!sessionId.equals(event.path("sessionId").asText())) return;
            String method = event.path("method").asText();
            if ("Page.loadEventFired".equals(method)) {
                loaded.complete(null);
            } else if ("Page.lifecycleEvent".equals(method)
                    && "networkIdle".equals(event.path("params").path("name").asText())) {
                networkIdle.complete(null);
            }
        });

        JsonNode nav = cdp.call("Page.navigate", Map.of("url", target.toString()), sessionId, remaining(deadline));
        String navError = nav.path("errorText").asText("");
        if (!navError.isBlank()) {
            throw new WebFetchException("Could not load " + target + ": " + navError);
        }

        await(loaded, remaining(deadline), "Page load timed out");
        // Best effort: give XHR/fetch-driven content a moment to land, but never fail on it.
        Duration settle = min(remaining(deadline), Duration.ofMillis(props.networkIdleWaitMs()));
        try {
            networkIdle.get(settle.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException ignored) {
            // proceed with whatever has rendered so far
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebFetchException("Interrupted while loading " + target, e);
        }

        String finalUrl = evaluate(cdp, sessionId, "location.href", deadline);
        String title    = evaluate(cdp, sessionId, "document.title", deadline);
        String markdown = cdp.call("LP.getMarkdown", Map.of(), sessionId, remaining(deadline))
                .path("markdown").asText("").strip();

        boolean truncated = markdown.length() > props.maxContentChars();
        if (truncated) markdown = markdown.substring(0, props.maxContentChars());

        log.info("[WebFetchService] Fetched {} ({} chars{})", finalUrl, markdown.length(),
                truncated ? ", truncated" : "");
        return new WebPage(finalUrl.isBlank() ? target.toString() : finalUrl, title, markdown, truncated);
    }

    private String evaluate(CdpConnection cdp, String sessionId, String expression, long deadline) {
        return cdp.call("Runtime.evaluate", Map.of("expression", expression, "returnByValue", true),
                        sessionId, remaining(deadline))
                .path("result").path("value").asText("");
    }

    private static void await(CompletableFuture<Void> future, Duration timeout, String timeoutMessage) {
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new WebFetchException(timeoutMessage, e);
        } catch (ExecutionException e) {
            throw new WebFetchException(timeoutMessage, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebFetchException("Interrupted", e);
        }
    }

    /** Time left until {@code deadline}; throws once it has passed so no CDP call starts with a zero timeout. */
    private static Duration remaining(long deadline) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw new WebFetchException("Web fetch timed out");
        return Duration.ofNanos(nanos);
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
