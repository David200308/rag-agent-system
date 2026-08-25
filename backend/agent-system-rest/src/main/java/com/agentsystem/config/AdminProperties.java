package com.agentsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * System-wide admin allowlist for the /api/v1/admin/** endpoints.
 *
 * admin.emails — comma-separated list of emails allowed to manage organizations.
 * Empty by default, which means the admin org endpoints reject everyone until configured.
 */
@ConfigurationProperties(prefix = "admin")
public record AdminProperties(
        List<String> emails
) {
    public AdminProperties {
        emails = emails == null ? List.of() : emails;
    }

    public boolean isAdmin(String email) {
        if (email == null) return false;
        return emails.stream().anyMatch(e -> e.equalsIgnoreCase(email));
    }
}
