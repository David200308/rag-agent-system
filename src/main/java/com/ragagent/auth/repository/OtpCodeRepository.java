package com.ragagent.auth.repository;

import com.ragagent.auth.entity.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    /** Find the latest unused, non-expired OTP for an email. */
    @Query("SELECT o FROM OtpCode o WHERE o.email = :email AND o.used = false AND o.expiresAt > :now ORDER BY o.createdAt DESC LIMIT 1")
    Optional<OtpCode> findValidOtp(String email, LocalDateTime now);

    /** Delete all OTPs for an email (cleanup after successful verify). */
    @Modifying
    @Transactional
    @Query("DELETE FROM OtpCode o WHERE o.email = :email")
    void deleteAllByEmail(String email);

    /** Delete expired OTPs (called periodically). */
    @Modifying
    @Transactional
    @Query("DELETE FROM OtpCode o WHERE o.expiresAt < :now")
    void deleteExpired(LocalDateTime now);
}
