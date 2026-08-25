package com.agentsystem.storage;

import com.agentsystem.org.OrgContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Thin pass-through to agent-system-storage-inner — this service never talks to
 * Garage or the image metadata table directly.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
@Tag(name = "Images", description = "Image upload/retrieval, backed by the storage-inner microservice")
public class ImageController {

    private final StorageClient storageClient;

    @PostMapping(consumes = "multipart/form-data")
    @Operation(summary = "Upload an image, optionally tagged to an entity (e.g. a travel record)")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "entityType", defaultValue = "GENERIC") String entityType,
            @RequestParam(value = "entityId", required = false) String entityId,
            HttpServletRequest req) {

        String ownerUuid = OrgContext.from(req).userUuid();
        try {
            StorageClient.UploadResult result = storageClient.uploadObject(
                    file.getBytes(), file.getOriginalFilename(), file.getContentType(),
                    ownerUuid, entityType, entityId);
            return ResponseEntity.status(201).body(result);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Could not read uploaded file"));
        } catch (RestClientResponseException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (RestClientException e) {
            log.warn("[ImageController] storage-inner unreachable: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", "Image storage unavailable"));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get image metadata and a short-lived download URL")
    public ResponseEntity<?> get(@PathVariable String id) {
        try {
            return ResponseEntity.ok(storageClient.getObject(id));
        } catch (RestClientResponseException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (RestClientException e) {
            log.warn("[ImageController] storage-inner unreachable: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", "Image storage unavailable"));
        }
    }

    @GetMapping
    @Operation(summary = "List images tagged to an entity, owned by the authenticated user")
    public ResponseEntity<?> list(
            @RequestParam String entityType,
            @RequestParam String entityId,
            HttpServletRequest req) {

        String ownerUuid = OrgContext.from(req).userUuid();
        try {
            List<StorageClient.ObjectMetadata> images = storageClient.listObjects(entityType, entityId, ownerUuid);
            return ResponseEntity.ok(images);
        } catch (RestClientResponseException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (RestClientException e) {
            log.warn("[ImageController] storage-inner unreachable: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", "Image storage unavailable"));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an image (owner only)")
    public ResponseEntity<?> delete(@PathVariable String id, HttpServletRequest req) {
        String ownerUuid = OrgContext.from(req).userUuid();
        try {
            storageClient.deleteObject(id, ownerUuid);
            return ResponseEntity.noContent().build();
        } catch (RestClientResponseException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (RestClientException e) {
            log.warn("[ImageController] storage-inner unreachable: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", "Image storage unavailable"));
        }
    }
}
