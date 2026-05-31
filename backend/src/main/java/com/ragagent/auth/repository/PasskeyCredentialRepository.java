package com.ragagent.auth.repository;

import com.ragagent.auth.entity.PasskeyCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, Long> {

    List<PasskeyCredential> findByEmail(String email);

    Optional<PasskeyCredential> findByCredentialId(String credentialId);

    Optional<PasskeyCredential> findByUserHandle(String userHandle);

    boolean existsByEmail(String email);

    @Modifying
    @Transactional
    @Query("DELETE FROM PasskeyCredential c WHERE c.email = :email")
    void deleteByEmail(String email);
}
