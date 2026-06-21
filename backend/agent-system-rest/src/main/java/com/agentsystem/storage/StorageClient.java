package com.agentsystem.storage;

import com.agentsystem.config.StorageProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Calls agent-system-storage-inner's internal REST API on behalf of authenticated users.
 * Uses X-Storage-Key authentication — no JWT needed (mirrors WorkflowScheduleClient).
 */
@Component
public class StorageClient {

    private final RestClient restClient;

    public StorageClient(StorageProperties props) {
        this.restClient = RestClient.builder()
                .baseUrl(props.url())
                .defaultHeader("X-Storage-Key", props.serviceKey())
                .build();
    }

    public record UploadResult(String id, String objectKey) {}

    public record ObjectMetadata(
            String id, String objectKey, String ownerEmail, String entityType,
            String entityId, String contentType, long sizeBytes, String url) {}

    public UploadResult uploadObject(byte[] content, String filename, String contentType,
                                      String ownerEmail, String entityType, String entityId) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(content) {
                    @Override
                    public String getFilename() { return filename; }
                })
                .contentType(MediaType.parseMediaType(
                        contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE));
        builder.part("ownerEmail", ownerEmail);
        builder.part("entityType", entityType);
        if (entityId != null) {
            builder.part("entityId", entityId);
        }

        return restClient.post()
                .uri("/internal/objects")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .body(UploadResult.class);
    }

    public ObjectMetadata getObject(String id) {
        return restClient.get()
                .uri("/internal/objects/{id}", id)
                .retrieve()
                .body(ObjectMetadata.class);
    }

    /** Raw bytes — used for text content (e.g. skills) where a presigned URL isn't needed. */
    public byte[] downloadObject(String id) {
        return restClient.get()
                .uri("/internal/objects/{id}/raw", id)
                .retrieve()
                .body(byte[].class);
    }

    public List<ObjectMetadata> listObjects(String entityType, String entityId, String ownerEmail) {
        ObjectMetadata[] objects = restClient.get()
                .uri("/internal/objects?entityType={t}&entityId={e}&ownerEmail={o}",
                        entityType, entityId, ownerEmail)
                .retrieve()
                .body(ObjectMetadata[].class);
        return objects != null ? List.of(objects) : List.of();
    }

    public void deleteObject(String id, String ownerEmail) {
        restClient.delete()
                .uri("/internal/objects/{id}?ownerEmail={o}", id, ownerEmail)
                .retrieve()
                .toBodilessEntity();
    }
}
