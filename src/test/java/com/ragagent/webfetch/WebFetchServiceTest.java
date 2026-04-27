package com.ragagent.webfetch;

import com.ragagent.config.WebFetchProperties;
import com.ragagent.webfetch.entity.WebFetchWhitelist;
import com.ragagent.webfetch.repository.WebFetchWhitelistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebFetchServiceTest {

    @Mock WebFetchWhitelistRepository whitelistRepo;

    // WebFetchProperties is a record (final) — instantiate directly
    private final WebFetchProperties enabledProps  = new WebFetchProperties(true,  10, 50_000);
    private final WebFetchProperties disabledProps = new WebFetchProperties(false, 10, 50_000);

    WebFetchService service;

    @BeforeEach
    void setUp() {
        service = new WebFetchService(enabledProps, whitelistRepo);
    }

    // ── listWhitelist ─────────────────────────────────────────────────────────

    @Test
    void listWhitelist_nullEmail_returnsGlobalList() {
        WebFetchWhitelist entry = new WebFetchWhitelist("example.com", null);
        when(whitelistRepo.findAllByOrderByDomainAsc()).thenReturn(List.of(entry));

        List<WebFetchWhitelist> result = service.listWhitelist(null);

        assertThat(result).containsExactly(entry);
        verify(whitelistRepo).findAllByOrderByDomainAsc();
        verify(whitelistRepo, never()).findAllByAddedByOrderByDomainAsc(any());
    }

    @Test
    void listWhitelist_withEmail_returnsUserList() {
        WebFetchWhitelist entry = new WebFetchWhitelist("example.com", "user@test.com");
        when(whitelistRepo.findAllByAddedByOrderByDomainAsc("user@test.com"))
                .thenReturn(List.of(entry));

        List<WebFetchWhitelist> result = service.listWhitelist("user@test.com");

        assertThat(result).containsExactly(entry);
        verify(whitelistRepo, never()).findAllByOrderByDomainAsc();
    }

    // ── addDomain ─────────────────────────────────────────────────────────────

    @Test
    void addDomain_newDomain_savesNormalised() {
        when(whitelistRepo.existsByDomainAndAddedBy("example.com", "user@test.com"))
                .thenReturn(false);
        when(whitelistRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        WebFetchWhitelist result = service.addDomain("EXAMPLE.COM", "user@test.com");

        ArgumentCaptor<WebFetchWhitelist> captor = ArgumentCaptor.forClass(WebFetchWhitelist.class);
        verify(whitelistRepo).save(captor.capture());
        assertThat(captor.getValue().getDomain()).isEqualTo("example.com");
    }

    @Test
    void addDomain_withSchemeInInput_stripsScheme() {
        when(whitelistRepo.existsByDomainAndAddedBy("example.com", "user@test.com"))
                .thenReturn(false);
        when(whitelistRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.addDomain("https://example.com/some/path", "user@test.com");

        ArgumentCaptor<WebFetchWhitelist> captor = ArgumentCaptor.forClass(WebFetchWhitelist.class);
        verify(whitelistRepo).save(captor.capture());
        assertThat(captor.getValue().getDomain()).isEqualTo("example.com");
    }

    @Test
    void addDomain_duplicate_throwsIllegalArgument() {
        when(whitelistRepo.existsByDomainAndAddedBy("example.com", "user@test.com"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.addDomain("example.com", "user@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already in your whitelist");
    }

    // ── removeDomain ──────────────────────────────────────────────────────────

    @Test
    void removeDomain_existingDomainWithUser_deletesIt() {
        when(whitelistRepo.existsByDomainAndAddedBy("example.com", "user@test.com"))
                .thenReturn(true);

        service.removeDomain("example.com", "user@test.com");

        verify(whitelistRepo).deleteByDomainAndAddedBy("example.com", "user@test.com");
    }

    @Test
    void removeDomain_notFoundForUser_throwsIllegalArgument() {
        when(whitelistRepo.existsByDomainAndAddedBy("example.com", "user@test.com"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.removeDomain("example.com", "user@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found in your whitelist");
    }

    @Test
    void removeDomain_nullUser_deletesGlobally() {
        when(whitelistRepo.existsByDomain("example.com")).thenReturn(true);

        service.removeDomain("example.com", null);

        verify(whitelistRepo).deleteByDomain("example.com");
    }

    @Test
    void removeDomain_nullUser_notFound_throwsIllegalArgument() {
        when(whitelistRepo.existsByDomain("example.com")).thenReturn(false);

        assertThatThrownBy(() -> service.removeDomain("example.com", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found in whitelist");
    }

    // ── isAllowed ─────────────────────────────────────────────────────────────

    @Test
    void isAllowed_exactDomainMatch_returnsTrue() {
        WebFetchWhitelist entry = new WebFetchWhitelist("example.com", "user@test.com");
        when(whitelistRepo.findAllByAddedByOrderByDomainAsc("user@test.com"))
                .thenReturn(List.of(entry));

        assertThat(service.isAllowed("example.com", "user@test.com")).isTrue();
    }

    @Test
    void isAllowed_subdomainMatch_returnsTrue() {
        WebFetchWhitelist entry = new WebFetchWhitelist("example.com", "user@test.com");
        when(whitelistRepo.findAllByAddedByOrderByDomainAsc("user@test.com"))
                .thenReturn(List.of(entry));

        assertThat(service.isAllowed("www.example.com", "user@test.com")).isTrue();
    }

    @Test
    void isAllowed_unlistedDomain_returnsFalse() {
        when(whitelistRepo.findAllByAddedByOrderByDomainAsc("user@test.com"))
                .thenReturn(List.of());

        assertThat(service.isAllowed("evil.com", "user@test.com")).isFalse();
    }

    @Test
    void isAllowed_partialMatchIsNotAllowed() {
        WebFetchWhitelist entry = new WebFetchWhitelist("example.com", "user@test.com");
        when(whitelistRepo.findAllByAddedByOrderByDomainAsc("user@test.com"))
                .thenReturn(List.of(entry));

        // "notexample.com" should NOT match "example.com"
        assertThat(service.isAllowed("notexample.com", "user@test.com")).isFalse();
    }

    // ── isUrlAllowed ──────────────────────────────────────────────────────────

    @Test
    void isUrlAllowed_malformedUrl_returnsFalse() {
        assertThat(service.isUrlAllowed("not-a-valid-url", "user@test.com")).isFalse();
    }

    @Test
    void isUrlAllowed_nonHttpScheme_returnsFalse() {
        assertThat(service.isUrlAllowed("ftp://example.com/file", "user@test.com")).isFalse();
    }

    @Test
    void isUrlAllowed_validUrlWithWhitelistedDomain_returnsTrue() {
        WebFetchWhitelist entry = new WebFetchWhitelist("example.com", "user@test.com");
        when(whitelistRepo.findAllByAddedByOrderByDomainAsc("user@test.com"))
                .thenReturn(List.of(entry));

        assertThat(service.isUrlAllowed("https://example.com/page", "user@test.com")).isTrue();
    }

    // ── fetch: guard checks ───────────────────────────────────────────────────

    @Test
    void fetch_webFetchDisabled_throwsIllegalState() {
        WebFetchService disabledService = new WebFetchService(disabledProps, whitelistRepo);

        assertThatThrownBy(() -> disabledService.fetch("https://example.com", "user@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void fetch_domainNotWhitelisted_throwsIllegalState() {
        when(whitelistRepo.findAllByAddedByOrderByDomainAsc("user@test.com"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.fetch("https://example.com/page", "user@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not whitelisted");
    }
}
