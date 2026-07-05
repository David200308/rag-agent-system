package com.agentsystem.webfetch.service;

import com.agentsystem.org.OrgContext;
import com.agentsystem.schema.DocumentResult;
import com.agentsystem.webfetch.entity.WebFetchWhitelist;

import java.util.List;

public interface WebFetchService {

    // ── Whitelist CRUD ────────────────────────────────────────────────────────

    List<WebFetchWhitelist> listWhitelist(OrgContext ctx);

    /** Backward-compatible personal-mode overload. */
    List<WebFetchWhitelist> listWhitelist(String userEmail);

    WebFetchWhitelist addDomain(String domain, OrgContext ctx);

    WebFetchWhitelist addDomain(String domain, String addedBy);

    void removeDomain(String domain, OrgContext ctx);

    void removeDomain(String domain, String userEmail);

    // ── Fetch ─────────────────────────────────────────────────────────────────

    /**
     * Fetch a single URL and return it as a {@link DocumentResult}.
     * Only domains in {@code userEmail}'s whitelist are allowed.
     * When {@code userEmail} is null (auth disabled), checks the global whitelist.
     *
     * @throws IllegalStateException if web-fetch is disabled or URL is not whitelisted
     * @throws IllegalArgumentException if the URL is malformed or uses a non-http(s) scheme
     */
    DocumentResult fetch(String url, String userEmail);

    /** Resolves ownership directly from ctx.userUuid() — no email bridge needed. */
    DocumentResult fetch(String url, OrgContext ctx);

    // ── Helpers ───────────────────────────────────────────────────────────────

    boolean isAllowed(String host, OrgContext ctx);

    boolean isAllowed(String host, String userEmail);

    boolean isUrlAllowed(String url, OrgContext ctx);

    boolean isUrlAllowed(String url, String userEmail);
}
