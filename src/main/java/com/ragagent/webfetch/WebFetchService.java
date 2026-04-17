package com.ragagent.webfetch;

import com.ragagent.config.WebFetchProperties;
import com.ragagent.schema.DocumentResult;
import com.ragagent.webfetch.entity.WebFetchWhitelist;
import com.ragagent.webfetch.repository.WebFetchWhitelistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

/**
 * Validates URLs against the domain whitelist and fetches their content.
 *
 * Web-fetch is disabled globally when {@code web-fetch.enabled=false}.
 * Each URL must match a whitelisted domain (exact host or any subdomain).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebFetchService {

    private final WebFetchProperties          props;
    private final WebFetchWhitelistRepository whitelistRepo;

    // ── Whitelist CRUD ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WebFetchWhitelist> listWhitelist() {
        return whitelistRepo.findAllByOrderByDomainAsc();
    }

    @Transactional
    public WebFetchWhitelist addDomain(String domain, String addedBy) {
        String normalized = normalizeDomain(domain);
        if (whitelistRepo.existsByDomain(normalized)) {
            throw new IllegalArgumentException("Domain already whitelisted: " + normalized);
        }
        return whitelistRepo.save(new WebFetchWhitelist(normalized, addedBy));
    }

    @Transactional
    public void removeDomain(String domain) {
        String normalized = normalizeDomain(domain);
        if (!whitelistRepo.existsByDomain(normalized)) {
            throw new IllegalArgumentException("Domain not found in whitelist: " + normalized);
        }
        whitelistRepo.deleteByDomain(normalized);
    }

    // ── Fetch ─────────────────────────────────────────────────────────────────

    /**
     * Fetch a single URL and return it as a {@link DocumentResult}.
     *
     * @throws IllegalStateException if web-fetch is disabled or URL is not whitelisted
     * @throws IllegalArgumentException if the URL is malformed or uses a non-http(s) scheme
     */
    public DocumentResult fetch(String url) {
        if (!props.enabled()) {
            throw new IllegalStateException("Web fetch is disabled.");
        }

        String host = extractHost(url);
        if (!isAllowed(host)) {
            throw new IllegalStateException(
                    "Domain not whitelisted: " + host + ". Add it via POST /api/v1/agent/web-fetch/whitelist");
        }

        try {
            log.info("[WebFetchService] Fetching: {}", url);
            org.jsoup.nodes.Document doc = Jsoup.connect(url)
                    .userAgent("RAG-Agent-WebFetch/1.0")
                    .timeout(props.timeoutSeconds() * 1000)
                    .get();

            String title   = doc.title();
            String text    = doc.body().text();
            if (text.length() > props.maxContentLengthChars()) {
                text = text.substring(0, props.maxContentLengthChars());
            }

            log.info("[WebFetchService] Fetched {} chars from {}", text.length(), url);
            return new DocumentResult(
                    url,
                    text,
                    1.0,   // fetched content is always treated as fully relevant
                    url,
                    Map.of("title", title, "fetchedFrom", url)
            );

        } catch (IOException e) {
            throw new IllegalStateException("Failed to fetch URL: " + url + " — " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isAllowed(String host) {
        String h = host.toLowerCase();
        return whitelistRepo.findAllByOrderByDomainAsc().stream()
                .anyMatch(w -> h.equals(w.getDomain()) || h.endsWith("." + w.getDomain()));
    }

    /**
     * Returns true if the URL's domain is present in the whitelist.
     * Returns false for malformed URLs or unsupported schemes.
     */
    public boolean isUrlAllowed(String url) {
        try {
            return isAllowed(extractHost(url));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String extractHost(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Only http/https URLs are supported: " + url);
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("Could not extract host from URL: " + url);
            }
            return host.toLowerCase();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Malformed URL: " + url, e);
        }
    }

    private String normalizeDomain(String domain) {
        // Strip any accidental scheme or path, lowercase
        return domain.toLowerCase().strip()
                .replaceAll("^https?://", "")
                .replaceAll("/.*$", "");
    }
}
