package com.agentsystem.user.repository;

import com.agentsystem.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Hibernate applies EmailAttributeConverter to the bound parameter automatically, so
     * this performs an equality match against the encrypted column transparently.
     */
    Optional<User> findByEmail(String email);

    Optional<User> findByUuid(String uuid);

    boolean existsByEmail(String email);
}
