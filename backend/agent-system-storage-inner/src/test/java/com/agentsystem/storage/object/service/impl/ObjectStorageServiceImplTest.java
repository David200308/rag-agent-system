package com.agentsystem.storage.object.service.impl;

import com.agentsystem.storage.config.GarageProperties;
import com.agentsystem.storage.object.entity.StoredObject;
import com.agentsystem.storage.object.repository.StoredObjectRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ObjectStorageServiceImplTest {

    @Mock S3Client s3Client;
    @Mock S3Presigner s3Presigner;
    @Mock StoredObjectRepository repository;

    // GarageProperties is a record (final) — instantiate directly
    GarageProperties garageProperties =
            new GarageProperties("http://localhost:3900", "garage", "GKaccesskey", "secretkey", "test-bucket");

    ObjectStorageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ObjectStorageServiceImpl(s3Client, s3Presigner, repository, garageProperties);
        lenient().when(repository.save(any(StoredObject.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static StoredObject object(String id, String objectKey, String ownerEmail) {
        return new StoredObject(id, objectKey, ownerEmail, "AVATAR", "ent-1", "image/png", 5L, Instant.now());
    }

    // ── upload ────────────────────────────────────────────────────────────────

    @Test
    void upload_knownImageType_appendsExtensionAndSaves() {
        StoredObject result = service.upload("abc".getBytes(), "image/jpeg", "user@test.com", "AVATAR", "ent-1");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).matches("avatar/.+\\.jpg");
        assertThat(captor.getValue().contentType()).isEqualTo("image/jpeg");

        assertThat(result.getOwnerEmail()).isEqualTo("user@test.com");
        assertThat(result.getEntityType()).isEqualTo("AVATAR");
        assertThat(result.getSizeBytes()).isEqualTo(3L);
        verify(repository).save(result);
    }

    @Test
    void upload_unknownContentType_noExtension() {
        StoredObject result = service.upload("x".getBytes(), "application/pdf", "user@test.com", "DOC", null);

        assertThat(result.getObjectKey()).matches("doc/[0-9a-f-]+$");
    }

    @Test
    void upload_nullContentType_noExtensionNoNPE() {
        StoredObject result = service.upload("x".getBytes(), null, "user@test.com", "DOC", null);

        assertThat(result.getObjectKey()).matches("doc/[0-9a-f-]+$");
        assertThat(result.getContentType()).isNull();
    }

    // ── find / list ───────────────────────────────────────────────────────────

    @Test
    void find_delegatesToRepository() {
        StoredObject obj = object("id-1", "avatar/id-1.png", "user@test.com");
        when(repository.findById("id-1")).thenReturn(Optional.of(obj));

        assertThat(service.find("id-1")).contains(obj);
    }

    @Test
    void list_delegatesToRepository() {
        List<StoredObject> objs = List.of(object("id-1", "k1", "user@test.com"));
        when(repository.findByEntityTypeAndEntityIdAndOwnerEmail("AVATAR", "ent-1", "user@test.com"))
                .thenReturn(objs);

        assertThat(service.list("AVATAR", "ent-1", "user@test.com")).isEqualTo(objs);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_ownerMatches_deletesFromS3AndRepo() {
        StoredObject obj = object("id-1", "avatar/id-1.png", "user@test.com");
        when(repository.findById("id-1")).thenReturn(Optional.of(obj));

        service.delete("id-1", "user@test.com");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("avatar/id-1.png");
        verify(repository).deleteById("id-1");
    }

    @Test
    void delete_objectNotFound_throwsNoSuchElementException() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("missing", "user@test.com"))
                .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(s3Client);
    }

    @Test
    void delete_wrongOwner_throwsSecurityExceptionAndSkipsS3() {
        StoredObject obj = object("id-1", "avatar/id-1.png", "owner@test.com");
        when(repository.findById("id-1")).thenReturn(Optional.of(obj));

        assertThatThrownBy(() -> service.delete("id-1", "attacker@test.com"))
                .isInstanceOf(SecurityException.class);
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(repository, never()).deleteById(any());
    }

    // ── presignedUrl ──────────────────────────────────────────────────────────

    @Test
    void presignedUrl_buildsRequestFromBucketAndKeyReturnsUrl() throws Exception {
        StoredObject obj = object("id-1", "avatar/id-1.png", "user@test.com");
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://garage.local/test-bucket/avatar/id-1.png?sig=abc").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        String url = service.presignedUrl(obj);

        assertThat(url).isEqualTo("https://garage.local/test-bucket/avatar/id-1.png?sig=abc");
    }

    // ── download ──────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void download_objectFound_returnsBytes() {
        StoredObject obj = object("id-1", "avatar/id-1.png", "user@test.com");
        when(repository.findById("id-1")).thenReturn(Optional.of(obj));
        ResponseBytes<GetObjectResponse> responseBytes = mock(ResponseBytes.class);
        when(responseBytes.asByteArray()).thenReturn("hello".getBytes());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        byte[] result = service.download("id-1");

        assertThat(result).isEqualTo("hello".getBytes());
    }

    @Test
    void download_objectNotFound_throwsNoSuchElementException() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download("missing"))
                .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(s3Client);
    }
}
