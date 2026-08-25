package com.agentsystem.webfetch.repository;

import com.agentsystem.webfetch.entity.WebFetchWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebFetchWhitelistRepository extends JpaRepository<WebFetchWhitelist, Long> {

    Optional<WebFetchWhitelist> findByDomain(String domain);

    boolean existsByDomain(String domain);

    List<WebFetchWhitelist> findAllByOrderByDomainAsc();

    void deleteByDomain(String domain);

    // ── Per-user (personal mode) queries ─────────────────────────────────────

    List<WebFetchWhitelist> findAllByAddedByUuidOrderByDomainAsc(String addedByUuid);

    boolean existsByDomainAndAddedByUuid(String domain, String addedByUuid);

    void deleteByDomainAndAddedByUuid(String domain, String addedByUuid);

    // ── Per-org (team mode) queries ───────────────────────────────────────────

    List<WebFetchWhitelist> findAllByOrgIdOrderByDomainAsc(String orgId);

    boolean existsByDomainAndOrgId(String domain, String orgId);

    void deleteByDomainAndOrgId(String domain, String orgId);
}
