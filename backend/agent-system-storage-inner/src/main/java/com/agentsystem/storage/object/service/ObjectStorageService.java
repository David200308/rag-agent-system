package com.agentsystem.storage.object.service;

import com.agentsystem.storage.object.entity.StoredObject;

import java.util.List;
import java.util.Optional;

public interface ObjectStorageService {

    StoredObject upload(byte[] content, String contentType, String ownerEmail,
                         String entityType, String entityId);

    Optional<StoredObject> find(String id);

    List<StoredObject> list(String entityType, String entityId, String ownerEmail);

    void delete(String id, String ownerEmail);

    /** Short-lived URL so the browser fetches bytes directly from Garage, not through this service. */
    String presignedUrl(StoredObject object);

    /** Streams the raw bytes back through this service — used for text content (e.g. skills) that doesn't need a presigned URL. */
    byte[] download(String id);
}
