package com.agentsystem.auth.repository;

import com.agentsystem.auth.entity.EmailWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailWhitelistRepository extends JpaRepository<EmailWhitelist, Long> {

    Optional<EmailWhitelist> findByEmailIgnoreCaseAndEnabledTrue(String email);
}
