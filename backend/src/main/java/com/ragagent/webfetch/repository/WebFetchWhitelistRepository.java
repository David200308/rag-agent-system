package com.ragagent.webfetch.repository;

import com.ragagent.webfetch.entity.WebFetchWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebFetchWhitelistRepository extends JpaRepository<WebFetchWhitelist, Long> {

    Optional<WebFetchWhitelist> findByDomain(String domain);

    boolean existsByDomain(String domain);

    List<WebFetchWhitelist> findAllByOrderByDomainAsc();

    void deleteByDomain(String domain);

    // ── Per-user queries ──────────────────────────────────────────────────────

    List<WebFetchWhitelist> findAllByAddedByOrderByDomainAsc(String addedBy);

    boolean existsByDomainAndAddedBy(String domain, String addedBy);

    void deleteByDomainAndAddedBy(String domain, String addedBy);
}
