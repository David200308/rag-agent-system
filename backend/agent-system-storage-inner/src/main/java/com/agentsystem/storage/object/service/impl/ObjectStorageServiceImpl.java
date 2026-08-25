package com.agentsystem.storage.object.service.impl;

import com.agentsystem.storage.object.service.ObjectStorageService;

import com.agentsystem.storage.object.entity.StoredObject;
import com.agentsystem.storage.object.repository.StoredObjectRepository;

import com.agentsystem.storage.config.GarageProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ObjectStorageServiceImpl implements ObjectStorageService {

    private static final Duration PRESIGN_TTL = Duration.ofMinutes(15);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StoredObjectRepository repository;
    private final GarageProperties garageProperties;

    @CircuitBreaker(name = "garage")
    @Retry(name = "garage")
    @Override
    public StoredObject upload(byte[] content, String contentType, String ownerUuid,
                                String entityType, String entityId) {
        String id = UUID.randomUUID().toString();
        String objectKey = entityType.toLowerCase() + "/" + id + extensionFor(contentType);

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(garageProperties.bucket())
                        .key(objectKey)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));

        StoredObject object = new StoredObject(id, objectKey, ownerUuid, entityType, entityId,
                contentType, content.length, Instant.now());
        return repository.save(object);
    }

    @Override
    public Optional<StoredObject> find(String id) {
        return repository.findById(id);
    }

    @Override
    public List<StoredObject> list(String entityType, String entityId, String ownerUuid) {
        return repository.findByEntityTypeAndEntityIdAndOwnerUuid(entityType, entityId, ownerUuid);
    }

    @CircuitBreaker(name = "garage")
    @Retry(name = "garage")
    @Override
    public void delete(String id, String ownerUuid) {
        StoredObject object = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("object not found: " + id));
        if (!object.getOwnerUuid().equals(ownerUuid)) {
            throw new SecurityException("not the owner of object " + id);
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(garageProperties.bucket())
                .key(object.getObjectKey())
                .build());
        repository.deleteById(id);
    }

    /** Short-lived URL so the browser fetches bytes directly from Garage, not through this service. */
    @Override
    public String presignedUrl(StoredObject object) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_TTL)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(garageProperties.bucket())
                        .key(object.getObjectKey())
                        .build())
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /** Streams the raw bytes back through this service — used for text content (e.g. skills) that doesn't need a presigned URL. */
    @CircuitBreaker(name = "garage")
    @Retry(name = "garage")
    @Override
    public byte[] download(String id) {
        StoredObject object = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("object not found: " + id));
        return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(garageProperties.bucket())
                        .key(object.getObjectKey())
                        .build())
                .asByteArray();
    }

    private String extensionFor(String contentType) {
        if (contentType == null) return "";
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> "";
        };
    }
}
