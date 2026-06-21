package com.agentsystem.storage.object;

import com.agentsystem.storage.config.StorageServiceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Internal-only API consumed by agent-system-rest's StorageClient.
 * Auth: shared {@code X-Storage-Key} header — NOT a user JWT.
 */
@Slf4j
@RestController
@RequestMapping("/internal/objects")
@RequiredArgsConstructor
public class InternalObjectController {

    private final ObjectStorageService objectStorageService;
    private final StorageServiceProperties serviceProperties;

    public record UploadResponse(String id, String objectKey) {}

    public record ObjectMetadataResponse(
            String id, String objectKey, String ownerEmail, String entityType,
            String entityId, String contentType, long sizeBytes, String url) {}

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(
            @RequestHeader(value = "X-Storage-Key", required = false) String serviceKey,
            @RequestParam("file") MultipartFile file,
            @RequestParam("ownerEmail") String ownerEmail,
            @RequestParam("entityType") String entityType,
            @RequestParam(value = "entityId", required = false) String entityId) {

        if (!validKey(serviceKey)) return ResponseEntity.status(401).build();

        try {
            StoredObject object = objectStorageService.upload(
                    file.getBytes(), file.getContentType(), ownerEmail, entityType, entityId);
            return ResponseEntity.status(201).body(new UploadResponse(object.getId(), object.getObjectKey()));
        } catch (IOException e) {
            log.warn("[InternalObjectController] upload failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ObjectMetadataResponse> get(
            @RequestHeader(value = "X-Storage-Key", required = false) String serviceKey,
            @PathVariable String id) {

        if (!validKey(serviceKey)) return ResponseEntity.status(401).build();

        return objectStorageService.find(id)
                .map(object -> ResponseEntity.ok(toResponse(object)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Streams raw bytes back — used for text content (e.g. skills) where a presigned URL isn't needed. */
    @GetMapping("/{id}/raw")
    public ResponseEntity<byte[]> getRaw(
            @RequestHeader(value = "X-Storage-Key", required = false) String serviceKey,
            @PathVariable String id) {

        if (!validKey(serviceKey)) return ResponseEntity.status(401).build();

        return objectStorageService.find(id)
                .map(object -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(object.getContentType()))
                        .body(objectStorageService.download(id)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<ObjectMetadataResponse>> list(
            @RequestHeader(value = "X-Storage-Key", required = false) String serviceKey,
            @RequestParam String entityType,
            @RequestParam String entityId,
            @RequestParam String ownerEmail) {

        if (!validKey(serviceKey)) return ResponseEntity.status(401).build();

        List<ObjectMetadataResponse> objects = objectStorageService.list(entityType, entityId, ownerEmail)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(objects);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "X-Storage-Key", required = false) String serviceKey,
            @PathVariable String id,
            @RequestParam String ownerEmail) {

        if (!validKey(serviceKey)) return ResponseEntity.status(401).build();

        try {
            objectStorageService.delete(id, ownerEmail);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    private boolean validKey(String serviceKey) {
        return serviceKey != null && serviceKey.equals(serviceProperties.serviceKey());
    }

    private ObjectMetadataResponse toResponse(StoredObject object) {
        return new ObjectMetadataResponse(object.getId(), object.getObjectKey(), object.getOwnerEmail(),
                object.getEntityType(), object.getEntityId(), object.getContentType(), object.getSizeBytes(),
                objectStorageService.presignedUrl(object));
    }
}
