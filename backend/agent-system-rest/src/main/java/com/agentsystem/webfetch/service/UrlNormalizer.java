package com.agentsystem.webfetch.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonical form of an http(s) URL used as a lookup key (fetch allowlist, Web Risk cache):
 * lowercase scheme/host, default port dropped, fragment dropped, empty path → "/",
 * trailing slash on a non-root path dropped. Path and query are otherwise kept exactly —
 * the query string is precisely where exfiltrated data would ride, so it must match.
 */
public final class UrlNormalizer {

    private static final Pattern URL_IN_TEXT = Pattern.compile("https?://[^\\s<>\"'`\\[\\]{}|\\\\^]+",
            Pattern.CASE_INSENSITIVE);

    private UrlNormalizer() {}

    /** @return the canonical form, or {@code null} if {@code url} isn't a valid http(s) URL */
    public static String normalize(String url) {
        try {
            return normalize(WebFetchService.parseUrl(url));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String normalize(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host   = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = port == -1
                || (scheme.equals("http") && port == 80)
                || (scheme.equals("https") && port == 443);

        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

        StringBuilder sb = new StringBuilder(scheme).append("://").append(host);
        if (!defaultPort) sb.append(':').append(port);
        sb.append(path);
        if (uri.getRawQuery() != null) sb.append('?').append(uri.getRawQuery());
        return sb.toString();
    }

    /** Extracts every http(s) URL appearing in free text (trailing sentence punctuation stripped). */
    public static List<String> extractUrls(String text) {
        List<String> urls = new ArrayList<>();
        if (text == null || text.isEmpty()) return urls;
        Matcher m = URL_IN_TEXT.matcher(text);
        while (m.find()) {
            String url = m.group().replaceAll("[.,;:!?)]+$", "");
            urls.add(url);
        }
        return urls;
    }
}
