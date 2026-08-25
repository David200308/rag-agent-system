package com.agentsystem.connector.repository;

import com.agentsystem.connector.entity.ConnectorToken;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConnectorTokenRepository extends JpaRepository<ConnectorToken, Long> {

    // Personal mode (orgId = null)
    Optional<ConnectorToken> findByOwnerUuidAndProviderAndOrgIdIsNull(String ownerUuid, String provider);
    List<ConnectorToken> findByOwnerUuidAndOrgIdIsNull(String ownerUuid);
    void deleteByOwnerUuidAndProviderAndOrgIdIsNull(String ownerUuid, String provider);

    // Team mode (orgId set)
    Optional<ConnectorToken> findByOwnerUuidAndProviderAndOrgId(String ownerUuid, String provider, String orgId);
    List<ConnectorToken> findByOwnerUuidAndOrgId(String ownerUuid, String orgId);
    void deleteByOwnerUuidAndProviderAndOrgId(String ownerUuid, String provider, String orgId);

    // Backward-compatible aliases → personal
    default Optional<ConnectorToken> findByOwnerUuidAndProvider(String ownerUuid, String provider) {
        return findByOwnerUuidAndProviderAndOrgIdIsNull(ownerUuid, provider);
    }
    default List<ConnectorToken> findByOwnerUuid(String ownerUuid) {
        return findByOwnerUuidAndOrgIdIsNull(ownerUuid);
    }
    default void deleteByOwnerUuidAndProvider(String ownerUuid, String provider) {
        deleteByOwnerUuidAndProviderAndOrgIdIsNull(ownerUuid, provider);
    }
}
