package com.agentsystem.webfetch.service;

import com.agentsystem.webfetch.service.impl.WebFetchServiceImpl;

import com.agentsystem.config.WebFetchProperties;
import com.agentsystem.org.OrgContext;
import com.agentsystem.user.entity.User;
import com.agentsystem.user.entity.UserStatus;
import com.agentsystem.user.service.UserAccountService;
import com.agentsystem.webfetch.entity.WebFetchWhitelist;
import com.agentsystem.webfetch.repository.WebFetchWhitelistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebFetchServiceTest {

    @Mock WebFetchWhitelistRepository whitelistRepo;
    @Mock UserAccountService          userAccountService;

    // WebFetchProperties is a record (final) — instantiate directly
    private final WebFetchProperties enabledProps  = new WebFetchProperties(true,  10, 50_000);
    private final WebFetchProperties disabledProps = new WebFetchProperties(false, 10, 50_000);

    WebFetchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WebFetchServiceImpl(enabledProps, whitelistRepo, userAccountService);
        lenient().when(userAccountService.findByEmail("user@test.com"))
                .thenReturn(Optional.of(new User("user@test.com", "user@test.com", UserStatus.USER, true)));
    }

    // ── listWhitelist ─────────────────────────────────────────────────────────

    @Test
    void listWhitelist_nullEmail_returnsGlobalList() {
        WebFetchWhitelist entry = new WebFetchWhitelist("example.com", null);
        when(whitelistRepo.findAllByOrderByDomainAsc()).thenReturn(List.of(entry));

        List<WebFetchWhitelist> result = service.listWhitelist((String) null);

        assertThat(result).containsExactly(entry);
        verify(whitelistRepo).findAllByOrderByDomainAsc();
        verify(whitelistRepo, never()).findAllByAddedByUuidOrderByDomainAsc(any());
    }

    @Test
    void listWhitelist_withEmail_returnsUserList() {
        WebFetchWhitelist entry = new WebFetchWhitelist("example.com", "user@test.com");
        when(whitelistRepo.findAllByAddedByUuidOrderByDomainAsc("user@test.com"))
                .thenReturn(List.of(entry));

        List<WebFetchWhitelist> result = service.listWhitelist("user@test.com");

        assertThat(result).containsExactly(entry);
        verify(whitelistRepo, never()).findAllByOrderByDomainAsc();
    }

    // ── addDomain ─────────────────────────────────────────────────────────────

    @Test
    void addDomain_newDomain_savesNormalised() {
        when(whitelistRepo.existsByDomainAndAddedByUuid("example.com", "user@test.com"))
                .thenReturn(false);
        when(whitelistRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        WebFetchWhitelist result = service.addDomain("EXAMPLE.COM", "user@test.com");

        ArgumentCaptor<WebFetchWhitelist> captor = ArgumentCaptor.forClass(WebFetchWhitelist.class);
        verify(whitelistRepo).save(captor.capture());
        assertThat(captor.getValue().getDomain()).isEqualTo("example.com");
    }

    @Test
    void addDomain_withSchemeInInput_stripsScheme() {
        when(whitelistRepo.existsByDomainAndAddedByUuid("example.com", "user@test.com"))
                .thenReturn(false);
        when(whitelistRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.addDomain("https://example.com/some/path", "user@test.com");

        ArgumentCaptor<WebFetchWhitelist> captor = ArgumentCaptor.forClass(WebFetchWhitelist.class);
        verify(whitelistRepo).save(captor.capture());
        assertThat(captor.getValue().getDomain()).isEqualTo("example.com");
    }

    @Test
    void addDomain_duplicate_throwsIllegalArgument() {
        when(whitelistRepo.existsByDomainAndAddedByUuid("example.com", "user@test.com"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.addDomain("example.com", "user@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already in your whitelist");
    }

    // ── removeDomain ──────────────────────────────────────────────────────────

    @Test
    void removeDomain_existingDomainWithUser_deletesIt() {
        when(whitelistRepo.existsByDomainAndAddedByUuid("example.com", "user@test.com"))
                .thenReturn(true);

        service.removeDomain("example.com", "user@test.com");

        verify(whitelistRepo).deleteByDomainAndAddedByUuid("example.com", "user@test.com");
    }

    @Test
    void removeDomain_notFoundForUser_throwsIllegalArgument() {
        when(whitelistRepo.existsByDomainAndAddedByUuid("example.com", "user@test.com"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.removeDomain("example.com", "user@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found in your whitelist");
    }

    @Test
    void removeDomain_nullUser_deletesGlobally() {
        when(whitelistRepo.existsByDomain("example.com")).thenReturn(true);

        service.removeDomain("example.com", (String) null);

        verify(whitelistRepo).deleteByDomain("example.com");
    }

    @Test
    void removeDomain_nullUser_notFound_throwsIllegalArgument() {
        when(whitelistRepo.existsByDomain("example.com")).thenReturn(false);

        assertThatThrownBy(() -> service.removeDomain("example.com", (String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found in whitelist");
    }

    // ── isAllowed ─────────────────────────────────────────────────────────────

    @Test
    void isAllowed_exactDomainMatch_returnsTrue() {
        WebFetchWhitelist entry = new WebFetchWhitelist("example.com", "user@test.com");
        when(whitelistRepo.findAllByAddedByUuidOrderByDomainAsc("user@test.com"))
                .thenReturn(List.of(entry));

        assertThat(service.isAllowed("example.com", "user@test.com")).isTrue();
    }

    @Test
    void isAllowed_subdomainMatch_returnsTrue() {
        WebFetchWhitelist entry = new WebFetchWhitelist("example.com", "user@test.com");
        when(whitelistRepo.findAllByAddedByUuidOrderByDomainAsc("user@test.com"))
                .thenReturn(List.of(entry));

        assertThat(service.isAllowed("www.example.com", "user@test.com")).isTrue();
    }

    @Test
    void isAllowed_unlistedDomain_returnsFalse() {
        when(whitelistRepo.findAllByAddedByUuidOrderByDomainAsc("user@test.com"))
                .thenReturn(List.of());

        assertThat(service.isAllowed("evil.com", "user@test.com")).isFalse();
    }

    @Test
    void isAllowed_partialMatchIsNotAllowed() {
        WebFetchWhitelist entry = new WebFetchWhitelist("example.com", "user@test.com");
        when(whitelistRepo.findAllByAddedByUuidOrderByDomainAsc("user@test.com"))
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
        when(whitelistRepo.findAllByAddedByUuidOrderByDomainAsc("user@test.com"))
                .thenReturn(List.of(entry));

        assertThat(service.isUrlAllowed("https://example.com/page", "user@test.com")).isTrue();
    }

    // ── fetch: guard checks ───────────────────────────────────────────────────

    @Test
    void fetch_webFetchDisabled_throwsIllegalState() {
        WebFetchServiceImpl disabledService = new WebFetchServiceImpl(disabledProps, whitelistRepo, userAccountService);

        assertThatThrownBy(() -> disabledService.fetch("https://example.com", "user@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void fetch_domainNotWhitelisted_throwsIllegalState() {
        when(whitelistRepo.findAllByAddedByUuidOrderByDomainAsc("user@test.com"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.fetch("https://example.com/page", "user@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not whitelisted");
    }

    // ── listWhitelist(OrgContext) ─────────────────────────────────────────────

    @Test
    void listWhitelist_orgContext_nullContext_returnsGlobal() {
        WebFetchWhitelist entry = new WebFetchWhitelist("global.com", null);
        when(whitelistRepo.findAllByOrderByDomainAsc()).thenReturn(List.of(entry));

        List<WebFetchWhitelist> result = service.listWhitelist((OrgContext) null);

        assertThat(result).containsExactly(entry);
        verify(whitelistRepo).findAllByOrderByDomainAsc();
    }

    @Test
    void listWhitelist_orgContext_teamMode_returnsOrgList() {
        OrgContext ctx = new OrgContext("user@test.com", "user@test.com", "TEAM", "skyproton");
        WebFetchWhitelist entry = new WebFetchWhitelist("example.com", "user@test.com", "skyproton");
        when(whitelistRepo.findAllByOrgIdOrderByDomainAsc("skyproton")).thenReturn(List.of(entry));

        List<WebFetchWhitelist> result = service.listWhitelist(ctx);

        assertThat(result).containsExactly(entry);
        verify(whitelistRepo).findAllByOrgIdOrderByDomainAsc("skyproton");
    }

    @Test
    void listWhitelist_orgContext_personalMode_returnsUserList() {
        OrgContext ctx = new OrgContext("user@test.com", "user@test.com", "PERSONAL", null);
        WebFetchWhitelist entry = new WebFetchWhitelist("example.com", "user@test.com");
        when(whitelistRepo.findAllByAddedByUuidOrderByDomainAsc("user@test.com")).thenReturn(List.of(entry));

        List<WebFetchWhitelist> result = service.listWhitelist(ctx);

        assertThat(result).containsExactly(entry);
        verify(whitelistRepo).findAllByAddedByUuidOrderByDomainAsc("user@test.com");
    }

    // ── addDomain(OrgContext) ─────────────────────────────────────────────────

    @Test
    void addDomain_teamMode_success() {
        OrgContext ctx = new OrgContext("user@test.com", "user@test.com", "TEAM", "skyproton");
        when(whitelistRepo.existsByDomainAndOrgId("example.com", "skyproton")).thenReturn(false);
        when(whitelistRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        WebFetchWhitelist result = service.addDomain("example.com", ctx);

        ArgumentCaptor<WebFetchWhitelist> captor = ArgumentCaptor.forClass(WebFetchWhitelist.class);
        verify(whitelistRepo).save(captor.capture());
        assertThat(captor.getValue().getDomain()).isEqualTo("example.com");
        assertThat(captor.getValue().getOrgId()).isEqualTo("skyproton");
    }

    @Test
    void addDomain_teamMode_duplicateThrows() {
        OrgContext ctx = new OrgContext("user@test.com", "user@test.com", "TEAM", "skyproton");
        when(whitelistRepo.existsByDomainAndOrgId("example.com", "skyproton")).thenReturn(true);

        assertThatThrownBy(() -> service.addDomain("example.com", ctx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already in org whitelist");
    }

    // ── removeDomain(OrgContext) ──────────────────────────────────────────────

    @Test
    void removeDomain_orgContext_teamMode_success() {
        OrgContext ctx = new OrgContext("user@test.com", "user@test.com", "TEAM", "skyproton");
        when(whitelistRepo.existsByDomainAndOrgId("example.com", "skyproton")).thenReturn(true);

        service.removeDomain("example.com", ctx);

        verify(whitelistRepo).deleteByDomainAndOrgId("example.com", "skyproton");
    }

    @Test
    void removeDomain_orgContext_personalWithEmail_success() {
        OrgContext ctx = new OrgContext("user@test.com", "user@test.com", "PERSONAL", null);
        when(whitelistRepo.existsByDomainAndAddedByUuid("example.com", "user@test.com")).thenReturn(true);

        service.removeDomain("example.com", ctx);

        verify(whitelistRepo).deleteByDomainAndAddedByUuid("example.com", "user@test.com");
    }

    @Test
    void removeDomain_orgContext_globalMode_success() {
        OrgContext ctx = new OrgContext(null, "PERSONAL", null);
        when(whitelistRepo.existsByDomain("example.com")).thenReturn(true);

        service.removeDomain("example.com", ctx);

        verify(whitelistRepo).deleteByDomain("example.com");
    }

    @Test
    void removeDomain_orgContext_globalMode_notFoundThrows() {
        OrgContext ctx = new OrgContext(null, "PERSONAL", null);
        when(whitelistRepo.existsByDomain("example.com")).thenReturn(false);

        assertThatThrownBy(() -> service.removeDomain("example.com", ctx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found in whitelist");
    }

    // ── isAllowed(OrgContext) ─────────────────────────────────────────────────

    @Test
    void isAllowed_teamMode_allowedDomain() {
        OrgContext ctx = new OrgContext("user@test.com", "user@test.com", "TEAM", "skyproton");
        WebFetchWhitelist entry = new WebFetchWhitelist("example.com", "user@test.com", "skyproton");
        when(whitelistRepo.findAllByOrgIdOrderByDomainAsc("skyproton")).thenReturn(List.of(entry));

        assertThat(service.isAllowed("example.com", ctx)).isTrue();
    }

    @Test
    void isAllowed_teamMode_notAllowedDomain() {
        OrgContext ctx = new OrgContext("user@test.com", "user@test.com", "TEAM", "skyproton");
        when(whitelistRepo.findAllByOrgIdOrderByDomainAsc("skyproton")).thenReturn(List.of());

        assertThat(service.isAllowed("evil.com", ctx)).isFalse();
    }

    // ── isUrlAllowed(OrgContext) ──────────────────────────────────────────────

    @Test
    void isUrlAllowed_orgContext_allowedUrl() {
        OrgContext ctx = new OrgContext("user@test.com", "user@test.com", "TEAM", "skyproton");
        WebFetchWhitelist entry = new WebFetchWhitelist("example.com", "user@test.com", "skyproton");
        when(whitelistRepo.findAllByOrgIdOrderByDomainAsc("skyproton")).thenReturn(List.of(entry));

        assertThat(service.isUrlAllowed("https://example.com/page", ctx)).isTrue();
    }

    @Test
    void isUrlAllowed_stringEmail_allowedUrl() {
        WebFetchWhitelist entry = new WebFetchWhitelist("example.com", "user@test.com");
        when(whitelistRepo.findAllByAddedByUuidOrderByDomainAsc("user@test.com")).thenReturn(List.of(entry));

        assertThat(service.isUrlAllowed("https://example.com/page", "user@test.com")).isTrue();
    }

    @Test
    void isUrlAllowed_orgContext_malformedUrl_returnsFalse() {
        OrgContext ctx = new OrgContext("user@test.com", "user@test.com", "PERSONAL", null);

        assertThat(service.isUrlAllowed("not-a-valid-url", ctx)).isFalse();
    }
}
