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

    /**
     * Lists whitelisted domains owned by {@code userEmail}.
     * When {@code userEmail} is null (auth disabled), returns all entries globally.
     */
    @Transactional(readOnly = true)
    public List<WebFetchWhitelist> listWhitelist(String userEmail) {
        if (userEmail == null) return whitelistRepo.findAllByOrderByDomainAsc();
        return whitelistRepo.findAllByAddedByOrderByDomainAsc(userEmail);
    }

    @Transactional
    public WebFetchWhitelist addDomain(String domain, String addedBy) {
        String normalized = normalizeDomain(domain);
        boolean exists = addedBy != null
                ? whitelistRepo.existsByDomainAndAddedBy(normalized, addedBy)
                : whitelistRepo.existsByDomain(normalized);
        if (exists) {
            throw new IllegalArgumentException("Domain already in your whitelist: " + normalized);
        }
        return whitelistRepo.save(new WebFetchWhitelist(normalized, addedBy));
    }

    /**
     * Removes a domain from {@code userEmail}'s whitelist.
     * When {@code userEmail} is null (auth disabled), removes globally.
     */
    @Transactional
    public void removeDomain(String domain, String userEmail) {
        String normalized = normalizeDomain(domain);
        if (userEmail != null) {
            if (!whitelistRepo.existsByDomainAndAddedBy(normalized, userEmail)) {
                throw new IllegalArgumentException("Domain not found in your whitelist: " + normalized);
            }
            whitelistRepo.deleteByDomainAndAddedBy(normalized, userEmail);
        } else {
            if (!whitelistRepo.existsByDomain(normalized)) {
                throw new IllegalArgumentException("Domain not found in whitelist: " + normalized);
            }
            whitelistRepo.deleteByDomain(normalized);
        }
    }

    // ── Fetch ─────────────────────────────────────────────────────────────────

    /**
     * Fetch a single URL and return it as a {@link DocumentResult}.
     * Only domains in {@code userEmail}'s whitelist are allowed.
     * When {@code userEmail} is null (auth disabled), checks the global whitelist.
     *
     * @throws IllegalStateException if web-fetch is disabled or URL is not whitelisted
     * @throws IllegalArgumentException if the URL is malformed or uses a non-http(s) scheme
     */
    public DocumentResult fetch(String url, String userEmail) {
        if (!props.enabled()) {
            throw new IllegalStateException("Web fetch is disabled.");
        }

        String host = extractHost(url);
        if (!isAllowed(host, userEmail)) {
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

    /**
     * Returns true if {@code host} matches an entry in {@code userEmail}'s whitelist.
     * When {@code userEmail} is null (auth disabled), checks the global whitelist.
     */
    public boolean isAllowed(String host, String userEmail) {
        String h = host.toLowerCase();
        List<WebFetchWhitelist> entries = userEmail != null
                ? whitelistRepo.findAllByAddedByOrderByDomainAsc(userEmail)
                : whitelistRepo.findAllByOrderByDomainAsc();
        return entries.stream()
                .anyMatch(w -> h.equals(w.getDomain()) || h.endsWith("." + w.getDomain()));
    }

    /**
     * Returns true if the URL's domain is in {@code userEmail}'s whitelist.
     * Returns false for malformed URLs or unsupported schemes.
     * When {@code userEmail} is null (auth disabled), checks globally.
     */
    public boolean isUrlAllowed(String url, String userEmail) {
        try {
            return isAllowed(extractHost(url), userEmail);
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
