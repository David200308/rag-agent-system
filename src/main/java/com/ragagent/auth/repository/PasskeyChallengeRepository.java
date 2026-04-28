package com.ragagent.auth.repository;

import com.ragagent.auth.entity.PasskeyChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface PasskeyChallengeRepository extends JpaRepository<PasskeyChallenge, Long> {

    Optional<PasskeyChallenge> findByEmailAndTypeAndExpiresAtAfter(String email, String type, Instant now);

    @Modifying
    @Transactional
    @Query("DELETE FROM PasskeyChallenge c WHERE c.email = :email AND c.type = :type")
    void deleteByEmailAndType(String email, String type);

    @Modifying
    @Transactional
    @Query("DELETE FROM PasskeyChallenge c WHERE c.expiresAt < :now")
    void deleteExpired(Instant now);
}
