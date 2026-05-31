package com.ragagent.auth.repository;

import com.ragagent.auth.entity.CliPublicKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CliPublicKeyRepository extends JpaRepository<CliPublicKey, Long> {
    Optional<CliPublicKey> findByUserEmail(String userEmail);
}
