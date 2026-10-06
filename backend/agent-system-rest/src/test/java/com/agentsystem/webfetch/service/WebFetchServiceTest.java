package com.agentsystem.webfetch.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebFetchServiceTest {

    @Test
    void parseUrl_acceptsHttpsAndTrimsWhitespace() {
        assertThat(WebFetchService.parseUrl("  https://example.com/a?b=c  ").toString())
                .isEqualTo("https://example.com/a?b=c");
    }

    @Test
    void parseUrl_acceptsUppercaseScheme() {
        assertThat(WebFetchService.parseUrl("HTTP://example.com").getHost()).isEqualTo("example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   ", "example.com", "file:///etc/passwd", "ftp://example.com",
            "javascript:alert(1)", "data:text/html,hi", "https://", "https://user:pass@example.com",
            "https://exa mple.com"
    })
    void parseUrl_rejectsNonHttpOrMalformed(String url) {
        assertThatThrownBy(() -> WebFetchService.parseUrl(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseUrl_rejectsNull() {
        assertThatThrownBy(() -> WebFetchService.parseUrl(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseUrl_rejectsOverlongUrl() {
        String url = "https://example.com/" + "a".repeat(WebFetchService.MAX_URL_LENGTH);
        assertThatThrownBy(() -> WebFetchService.parseUrl(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too long");
    }
}
