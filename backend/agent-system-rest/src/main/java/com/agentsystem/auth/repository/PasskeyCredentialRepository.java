package com.agentsystem.auth.repository;

import com.agentsystem.auth.entity.PasskeyCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, Long> {

    List<PasskeyCredential> findByUserUuid(String userUuid);

    Optional<PasskeyCredential> findByCredentialId(String credentialId);

    Optional<PasskeyCredential> findByUserHandle(String userHandle);

    boolean existsByUserUuid(String userUuid);

    @Modifying
    @Transactional
    @Query("DELETE FROM PasskeyCredential c WHERE c.userUuid = :userUuid")
    void deleteByUserUuid(String userUuid);
}
