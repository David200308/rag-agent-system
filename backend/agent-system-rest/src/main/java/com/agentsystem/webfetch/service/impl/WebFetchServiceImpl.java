package com.agentsystem.webfetch.service.impl;

import com.agentsystem.webfetch.service.WebFetchService;

import com.agentsystem.config.WebFetchProperties;
import com.agentsystem.org.OrgContext;
import com.agentsystem.schema.DocumentResult;
import com.agentsystem.user.entity.User;
import com.agentsystem.user.service.UserAccountService;
import com.agentsystem.webfetch.entity.WebFetchWhitelist;
import com.agentsystem.webfetch.repository.WebFetchWhitelistRepository;
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
public class WebFetchServiceImpl implements WebFetchService {

    private final WebFetchProperties          props;
    private final WebFetchWhitelistRepository whitelistRepo;
    private final UserAccountService          userAccountService;

    // ── Whitelist CRUD ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Override
    public List<WebFetchWhitelist> listWhitelist(OrgContext ctx) {
        if (ctx == null || ctx.userUuid() == null) return whitelistRepo.findAllByOrderByDomainAsc();
        if (ctx.isTeam()) return whitelistRepo.findAllByOrgIdOrderByDomainAsc(ctx.orgId());
        return whitelistRepo.findAllByAddedByUuidOrderByDomainAsc(ctx.userUuid());
    }

    /** Backward-compatible personal-mode overload. Resolves email to a user_uuid internally. */
    @Transactional(readOnly = true)
    @Override
    public List<WebFetchWhitelist> listWhitelist(String userEmail) {
        String uuid = resolveUuid(userEmail);
        if (uuid == null) return whitelistRepo.findAllByOrderByDomainAsc();
        return whitelistRepo.findAllByAddedByUuidOrderByDomainAsc(uuid);
    }

    @Transactional
    @Override
    public WebFetchWhitelist addDomain(String domain, OrgContext ctx) {
        String normalized = normalizeDomain(domain);
        if (ctx != null && ctx.isTeam()) {
            if (whitelistRepo.existsByDomainAndOrgId(normalized, ctx.orgId())) {
                throw new IllegalArgumentException("Domain already in org whitelist: " + normalized);
            }
            return whitelistRepo.save(new WebFetchWhitelist(normalized, ctx.userUuid(), ctx.orgId()));
        }
        String addedByUuid = ctx != null ? ctx.userUuid() : null;
        boolean exists = addedByUuid != null
                ? whitelistRepo.existsByDomainAndAddedByUuid(normalized, addedByUuid)
                : whitelistRepo.existsByDomain(normalized);
        if (exists) {
            throw new IllegalArgumentException("Domain already in your whitelist: " + normalized);
        }
        return whitelistRepo.save(new WebFetchWhitelist(normalized, addedByUuid));
    }

    @Transactional
    @Override
    public WebFetchWhitelist addDomain(String domain, String addedBy) {
        return addDomain(domain, new OrgContext(resolveUuid(addedBy), addedBy, "PERSONAL", null));
    }

    @Transactional
    @Override
    public void removeDomain(String domain, OrgContext ctx) {
        String normalized = normalizeDomain(domain);
        if (ctx != null && ctx.isTeam()) {
            if (!whitelistRepo.existsByDomainAndOrgId(normalized, ctx.orgId())) {
                throw new IllegalArgumentException("Domain not found in org whitelist: " + normalized);
            }
            whitelistRepo.deleteByDomainAndOrgId(normalized, ctx.orgId());
            return;
        }
        String userUuid = ctx != null ? ctx.userUuid() : null;
        if (userUuid != null) {
            if (!whitelistRepo.existsByDomainAndAddedByUuid(normalized, userUuid)) {
                throw new IllegalArgumentException("Domain not found in your whitelist: " + normalized);
            }
            whitelistRepo.deleteByDomainAndAddedByUuid(normalized, userUuid);
        } else {
            if (!whitelistRepo.existsByDomain(normalized)) {
                throw new IllegalArgumentException("Domain not found in whitelist: " + normalized);
            }
            whitelistRepo.deleteByDomain(normalized);
        }
    }

    @Transactional
    @Override
    public void removeDomain(String domain, String userEmail) {
        removeDomain(domain, new OrgContext(resolveUuid(userEmail), userEmail, "PERSONAL", null));
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
    @Override
    public DocumentResult fetch(String url, String userEmail) {
        return fetch(url, new OrgContext(resolveUuid(userEmail), userEmail, "PERSONAL", null));
    }

    @Override
    public DocumentResult fetch(String url, OrgContext ctx) {
        if (!props.enabled()) {
            throw new IllegalStateException("Web fetch is disabled.");
        }

        String host = extractHost(url);
        if (!isAllowed(host, ctx)) {
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

    @Override
    public boolean isAllowed(String host, OrgContext ctx) {
        String h = host.toLowerCase();
        List<WebFetchWhitelist> entries;
        if (ctx != null && ctx.isTeam()) {
            entries = whitelistRepo.findAllByOrgIdOrderByDomainAsc(ctx.orgId());
        } else if (ctx != null && ctx.userUuid() != null) {
            entries = whitelistRepo.findAllByAddedByUuidOrderByDomainAsc(ctx.userUuid());
        } else {
            entries = whitelistRepo.findAllByOrderByDomainAsc();
        }
        return entries.stream()
                .anyMatch(w -> h.equals(w.getDomain()) || h.endsWith("." + w.getDomain()));
    }

    @Override
    public boolean isAllowed(String host, String userEmail) {
        return isAllowed(host, new OrgContext(resolveUuid(userEmail), userEmail, "PERSONAL", null));
    }

    @Override
    public boolean isUrlAllowed(String url, OrgContext ctx) {
        try {
            return isAllowed(extractHost(url), ctx);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
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

    /** Resolves an email to its user_uuid, or null if no such email is a registered user. */
    private String resolveUuid(String email) {
        if (email == null || email.isBlank()) return null;
        return userAccountService.findByEmail(email).map(User::getUuid).orElse(null);
    }
}
