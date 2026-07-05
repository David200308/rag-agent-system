package com.agentsystem.auth.repository;

import com.agentsystem.auth.entity.CliPublicKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CliPublicKeyRepository extends JpaRepository<CliPublicKey, Long> {
    Optional<CliPublicKey> findByUserUuid(String userUuid);
}
