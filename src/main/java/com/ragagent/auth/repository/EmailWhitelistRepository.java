package com.ragagent.auth.repository;

import com.ragagent.auth.entity.EmailWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailWhitelistRepository extends JpaRepository<EmailWhitelist, Long> {

    Optional<EmailWhitelist> findByEmailIgnoreCaseAndEnabledTrue(String email);
}
