package com.ragagent.connector;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConnectorTokenRepository extends JpaRepository<ConnectorToken, Long> {
    Optional<ConnectorToken> findByOwnerEmailAndProvider(String ownerEmail, String provider);
    List<ConnectorToken> findByOwnerEmail(String ownerEmail);
    void deleteByOwnerEmailAndProvider(String ownerEmail, String provider);
}
