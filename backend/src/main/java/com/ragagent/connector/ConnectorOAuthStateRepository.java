package com.ragagent.connector;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ConnectorOAuthStateRepository extends JpaRepository<ConnectorOAuthState, Long> {
    Optional<ConnectorOAuthState> findByState(String state);

    @Modifying
    @Transactional
    @Query("DELETE FROM ConnectorOAuthState s WHERE s.expiresAt < :now")
    void deleteExpired(LocalDateTime now);
}
