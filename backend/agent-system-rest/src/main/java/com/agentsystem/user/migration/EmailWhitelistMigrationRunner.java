package com.agentsystem.user.migration;

import com.agentsystem.user.entity.User;
import com.agentsystem.user.entity.UserStatus;
import com.agentsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One-time, opt-in copy of the old email_whitelist table into the new `users` table. Off
 * by default. Enable with app.migrate-email-whitelist=true for a single deploy, verify the
 * `users` table looks right, then drop email_whitelist by hand.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailWhitelistMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate    jdbcTemplate;
    private final UserRepository  userRepository;

    @Value("${app.migrate-email-whitelist:false}")
    private boolean migrateEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!migrateEnabled) return;

        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList("SELECT email, enabled FROM email_whitelist");
        } catch (BadSqlGrammarException e) {
            log.info("[EmailWhitelistMigration] email_whitelist table not found, nothing to migrate");
            return;
        }

        int migrated = 0, skipped = 0;
        for (Map<String, Object> row : rows) {
            String email = ((String) row.get("email")).trim().toLowerCase();
            boolean enabled = Boolean.TRUE.equals(row.get("enabled"));

            if (userRepository.existsByEmail(email)) {
                skipped++;
                continue;
            }
            // Whitelist rows were already-approved users under the old system, not
            // pending signups, so they land directly at USER (not PRE_USER).
            userRepository.save(new User(UUID.randomUUID().toString(), email, UserStatus.USER, enabled));
            migrated++;
        }

        log.info("[EmailWhitelistMigration] Migrated {} row(s), skipped {} already-present row(s). " +
                "Verify the `users` table, then drop `email_whitelist` manually.", migrated, skipped);
    }
}
