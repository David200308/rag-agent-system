package com.ragagent.webfetch.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A single allowed domain in the web-fetch whitelist.
 *
 * Matching rule: a URL is allowed if its host equals the stored domain
 * OR if the host ends with ".<domain>" (i.e. any subdomain is also allowed).
 *
 * Example:  domain = "example.com"
 *   ✓  https://example.com/page
 *   ✓  https://www.example.com/page
 *   ✗  https://notexample.com/page
 */
@Entity
@Table(name = "web_fetch_whitelist",
       uniqueConstraints = @UniqueConstraint(name = "uq_wfw_domain_user", columnNames = {"domain", "added_by"}))
@Getter
@Setter
@NoArgsConstructor
public class WebFetchWhitelist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Bare domain, e.g. "example.com" (no scheme, no trailing slash). */
    @Column(nullable = false, length = 253)
    private String domain;

    @Column(name = "added_by", length = 255)
    private String addedBy;

    /** Org slug when in team mode; null = personal. */
    @Column(name = "org_id", length = 100)
    private String orgId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public WebFetchWhitelist(String domain, String addedBy) {
        this(domain, addedBy, null);
    }

    public WebFetchWhitelist(String domain, String addedBy, String orgId) {
        this.domain  = domain.toLowerCase().strip();
        this.addedBy = addedBy;
        this.orgId   = orgId;
    }
}
