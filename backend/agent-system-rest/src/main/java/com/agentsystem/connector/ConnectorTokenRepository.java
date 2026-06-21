package com.agentsystem.connector;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConnectorTokenRepository extends JpaRepository<ConnectorToken, Long> {

    // Personal mode (orgId = null)
    Optional<ConnectorToken> findByOwnerEmailAndProviderAndOrgIdIsNull(String ownerEmail, String provider);
    List<ConnectorToken> findByOwnerEmailAndOrgIdIsNull(String ownerEmail);
    void deleteByOwnerEmailAndProviderAndOrgIdIsNull(String ownerEmail, String provider);

    // Team mode (orgId set)
    Optional<ConnectorToken> findByOwnerEmailAndProviderAndOrgId(String ownerEmail, String provider, String orgId);
    List<ConnectorToken> findByOwnerEmailAndOrgId(String ownerEmail, String orgId);
    void deleteByOwnerEmailAndProviderAndOrgId(String ownerEmail, String provider, String orgId);

    // Backward-compatible aliases → personal
    default Optional<ConnectorToken> findByOwnerEmailAndProvider(String ownerEmail, String provider) {
        return findByOwnerEmailAndProviderAndOrgIdIsNull(ownerEmail, provider);
    }
    default List<ConnectorToken> findByOwnerEmail(String ownerEmail) {
        return findByOwnerEmailAndOrgIdIsNull(ownerEmail);
    }
    default void deleteByOwnerEmailAndProvider(String ownerEmail, String provider) {
        deleteByOwnerEmailAndProviderAndOrgIdIsNull(ownerEmail, provider);
    }
}
