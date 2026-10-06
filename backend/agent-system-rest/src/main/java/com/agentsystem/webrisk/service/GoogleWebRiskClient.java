package com.agentsystem.webrisk.service;

import com.agentsystem.config.WebRiskProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin client for the Web Risk Lookup API:
 * {@code GET https://webrisk.googleapis.com/v1/uris:search?uri=..&threatTypes=..&key=..}
 * A clean URL returns {@code {}}; a match returns
 * {@code {"threat":{"threatTypes":["MALWARE"],"expireTime":"..."}}}.
 */
@Component
public class GoogleWebRiskClient {

    static final String ENDPOINT = "https://webrisk.googleapis.com/v1/uris:search";
    static final List<String> THREAT_TYPES = List.of("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE");

    private final WebRiskProperties props;
    private final ObjectMapper      mapper;
    private final HttpClient        httpClient;

    public GoogleWebRiskClient(WebRiskProperties props, ObjectMapper mapper) {
        this.props      = props;
        this.mapper     = mapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.timeoutMs()))
                .build();
    }

    /**
     * @return the raw lookup result: threat types matched (empty = clean) and Google's
     *         expireTime for a match ({@code null} for a clean URL — caller picks the TTL)
     */
    public LookupResult lookup(String url) throws IOException, InterruptedException {
        StringBuilder query = new StringBuilder("?uri=").append(URLEncoder.encode(url, StandardCharsets.UTF_8));
        for (String t : THREAT_TYPES) query.append("&threatTypes=").append(t);
        query.append("&key=").append(URLEncoder.encode(props.apiKey(), StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT + query))
                .timeout(Duration.ofMillis(props.timeoutMs()))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            // Never include the request URI here — it carries the API key.
            throw new IOException("Web Risk API returned HTTP " + resp.statusCode());
        }
        return parse(mapper.readTree(resp.body()));
    }

    static LookupResult parse(JsonNode root) {
        JsonNode threat = root.path("threat");
        if (threat.isMissingNode() || threat.isNull()) {
            return new LookupResult(List.of(), null);
        }
        List<String> types = new ArrayList<>();
        threat.path("threatTypes").forEach(t -> types.add(t.asText()));
        Instant expire = null;
        String expireText = threat.path("expireTime").asText("");
        if (!expireText.isBlank()) {
            try {
                expire = Instant.parse(expireText);
            } catch (Exception ignored) {
                // fall back to the configured unsafe TTL
            }
        }
        return new LookupResult(types, expire);
    }

    public record LookupResult(List<String> threatTypes, Instant expireTime) {}
}
