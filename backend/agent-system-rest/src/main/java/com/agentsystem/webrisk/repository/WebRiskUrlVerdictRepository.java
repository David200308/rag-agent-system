package com.agentsystem.webrisk.repository;

import com.agentsystem.webrisk.entity.WebRiskUrlVerdict;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface WebRiskUrlVerdictRepository extends JpaRepository<WebRiskUrlVerdict, String> {

    Optional<WebRiskUrlVerdict> findByUrlHashAndExpiresAtAfter(String urlHash, Instant now);

    @Modifying
    @Transactional
    @Query("DELETE FROM WebRiskUrlVerdict v WHERE v.expiresAt < :now")
    int deleteExpired(Instant now);
}
