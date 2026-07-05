package com.agentsystem.storage.object.repository;

import com.agentsystem.storage.object.entity.StoredObject;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoredObjectRepository extends JpaRepository<StoredObject, String> {

    List<StoredObject> findByEntityTypeAndEntityIdAndOwnerUuid(String entityType, String entityId, String ownerUuid);
}
