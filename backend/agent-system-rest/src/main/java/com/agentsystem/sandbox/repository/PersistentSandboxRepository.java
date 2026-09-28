package com.agentsystem.sandbox.repository;

import com.agentsystem.sandbox.entity.PersistentSandbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistentSandboxRepository extends JpaRepository<PersistentSandbox, String> {

    List<PersistentSandbox> findByOwnerUuidOrderByCreatedAtDesc(String ownerUuid);

    long countByOwnerUuid(String ownerUuid);
}
