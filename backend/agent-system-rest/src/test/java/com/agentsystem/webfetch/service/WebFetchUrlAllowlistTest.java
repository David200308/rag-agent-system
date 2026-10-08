package com.agentsystem.webfetch.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WebFetchUrlAllowlistTest {

    final WebFetchUrlAllowlist allowlist = new WebFetchUrlAllowlist();

    @AfterEach
    void tearDown() {
        allowlist.clear();
    }

    @Test
    void notPrimed_deniesEverything() {
        assertThat(allowlist.isAllowed("https://example.com")).isFalse();
    }

    @Test
    void reset_allowsUrlsFoundInUserText_withSentencePunctuationStripped() {
        allowlist.reset(List.of("Please read https://example.com/docs/page. Also (https://other.org/a?b=1)!"));

        assertThat(allowlist.isAllowed("https://example.com/docs/page")).isTrue();
        assertThat(allowlist.isAllowed("https://other.org/a?b=1")).isTrue();
    }

    @Test
    void matching_ignoresCosmeticDifferences() {
        allowlist.reset(List.of("https://Example.COM:443/docs/#intro"));

        assertThat(allowlist.isAllowed("https://example.com/docs")).isTrue();
        assertThat(allowlist.isAllowed("HTTPS://example.com/docs/")).isTrue();
    }

    @Test
    void modelAuthoredVariants_areRefused() {
        allowlist.reset(List.of("https://example.com/page"));

        assertThat(allowlist.isAllowed("https://example.com/page?d=secret")).isFalse();
        assertThat(allowlist.isAllowed("https://example.com/other")).isFalse();
        assertThat(allowlist.isAllowed("https://attacker.example/?d=secret")).isFalse();
        assertThat(allowlist.isAllowed("http://example.com/page")).isFalse();
    }

    @Test
    void allow_addsSearchResultUrlsForTheCurrentRequest() {
        allowlist.reset(List.of("no urls here"));
        allowlist.allow("https://result.example/article");

        assertThat(allowlist.isAllowed("https://result.example/article")).isTrue();
    }

    @Test
    void allow_outsideRequestScope_isNoOp() {
        allowlist.allow("https://result.example/article");

        assertThat(allowlist.isAllowed("https://result.example/article")).isFalse();
    }

    @Test
    void priorSearchHashes_allowEarlierTurnsResults_exactlyAsNormalized() {
        String prior = UrlNormalizer.hash(UrlNormalizer.normalize("https://www.theindependenthotel.it/"));
        allowlist.reset(List.of("no urls here"), Set.of(prior));

        assertThat(allowlist.isAllowed("https://WWW.theindependenthotel.it")).isTrue();
        assertThat(allowlist.isAllowed("https://www.theindependenthotel.it/?d=secret")).isFalse();
        assertThat(allowlist.isAllowed("https://www.theindependenthotel.it/rooms")).isFalse();
    }

    @Test
    void searchResultUrls_returnsOnlyThisRequestsSearchResults() {
        allowlist.reset(List.of("see https://user.example/page"),
                Set.of(UrlNormalizer.hash("https://prior.example/")));
        allowlist.allow("https://Result.example/article/");

        assertThat(allowlist.searchResultUrls()).containsExactly("https://result.example/article");
    }

    @Test
    void searchResultUrls_outsideRequestScope_isEmpty() {
        assertThat(allowlist.searchResultUrls()).isEmpty();
    }

    @Test
    void clear_endsTheScope() {
        allowlist.reset(List.of("https://example.com"));
        allowlist.clear();

        assertThat(allowlist.isAllowed("https://example.com")).isFalse();
    }

    @Test
    void scope_isPerThread() throws Exception {
        allowlist.reset(List.of("https://example.com"));
        boolean[] seenOnOtherThread = new boolean[1];
        Thread t = new Thread(() -> seenOnOtherThread[0] = allowlist.isAllowed("https://example.com"));
        t.start();
        t.join();

        assertThat(seenOnOtherThread[0]).isFalse();
    }
}
