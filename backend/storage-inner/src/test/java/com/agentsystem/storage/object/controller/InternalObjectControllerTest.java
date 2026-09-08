package com.agentsystem.storage.object.controller;

import com.agentsystem.storage.config.StorageServiceProperties;
import com.agentsystem.storage.object.entity.StoredObject;
import com.agentsystem.storage.object.service.ObjectStorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalObjectControllerTest {

    @Mock ObjectStorageService objectStorageService;

    // StorageServiceProperties is a record (final) — instantiate directly
    StorageServiceProperties serviceProperties = new StorageServiceProperties("secret-key");

    InternalObjectController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalObjectController(objectStorageService, serviceProperties);
    }

    private static StoredObject object(String id, String objectKey) {
        return new StoredObject(id, objectKey, "user@test.com", "AVATAR", "ent-1", "image/png", 5L, Instant.now());
    }

    // ── upload ────────────────────────────────────────────────────────────────

    @Test
    void upload_nullServiceKey_returns401() {
        MultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "hello".getBytes());

        ResponseEntity<?> resp = controller.upload(null, file, "user@test.com", "AVATAR", null);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(objectStorageService);
    }

    @Test
    void upload_wrongServiceKey_returns401() {
        MultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "hello".getBytes());

        ResponseEntity<?> resp = controller.upload("wrong-key", file, "user@test.com", "AVATAR", null);

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(objectStorageService);
    }

    @Test
    void upload_validKey_returns201WithIdAndObjectKey() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "hello".getBytes());
        when(objectStorageService.upload(eq("hello".getBytes()), eq("image/png"), eq("user@test.com"),
                eq("AVATAR"), eq(null)))
                .thenReturn(object("id-1", "avatar/id-1.png"));

        ResponseEntity<InternalObjectController.UploadResponse> resp =
                controller.upload("secret-key", file, "user@test.com", "AVATAR", null);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody().id()).isEqualTo("id-1");
        assertThat(resp.getBody().objectKey()).isEqualTo("avatar/id-1.png");
    }

    @Test
    void upload_validKey_ioExceptionReadingFile_returns500() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenThrow(new IOException("boom"));

        ResponseEntity<?> resp = controller.upload("secret-key", file, "user@test.com", "AVATAR", null);

        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        verifyNoInteractions(objectStorageService);
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @Test
    void get_nullServiceKey_returns401() {
        ResponseEntity<?> resp = controller.get(null, "id-1");

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(objectStorageService);
    }

    @Test
    void get_validKey_found_returns200WithMetadata() {
        StoredObject obj = object("id-1", "avatar/id-1.png");
        when(objectStorageService.find("id-1")).thenReturn(Optional.of(obj));
        when(objectStorageService.presignedUrl(obj)).thenReturn("https://signed-url");

        ResponseEntity<InternalObjectController.ObjectMetadataResponse> resp = controller.get("secret-key", "id-1");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().id()).isEqualTo("id-1");
        assertThat(resp.getBody().objectKey()).isEqualTo("avatar/id-1.png");
        assertThat(resp.getBody().ownerUuid()).isEqualTo("user@test.com");
        assertThat(resp.getBody().url()).isEqualTo("https://signed-url");
    }

    @Test
    void get_validKey_notFound_returns404() {
        when(objectStorageService.find("missing")).thenReturn(Optional.empty());

        ResponseEntity<?> resp = controller.get("secret-key", "missing");

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── getRaw ────────────────────────────────────────────────────────────────

    @Test
    void getRaw_nullServiceKey_returns401() {
        ResponseEntity<?> resp = controller.getRaw(null, "id-1");

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(objectStorageService);
    }

    @Test
    void getRaw_validKey_found_returns200WithBytesAndContentType() {
        StoredObject obj = object("id-1", "avatar/id-1.png");
        obj.setContentType("text/plain");
        when(objectStorageService.find("id-1")).thenReturn(Optional.of(obj));
        when(objectStorageService.download("id-1")).thenReturn("hello".getBytes());

        ResponseEntity<byte[]> resp = controller.getRaw("secret-key", "id-1");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEqualTo("hello".getBytes());
        assertThat(resp.getHeaders().getContentType().toString()).isEqualTo("text/plain");
    }

    @Test
    void getRaw_validKey_notFound_returns404() {
        when(objectStorageService.find("missing")).thenReturn(Optional.empty());

        ResponseEntity<byte[]> resp = controller.getRaw("secret-key", "missing");

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_nullServiceKey_returns401() {
        ResponseEntity<?> resp = controller.list(null, "AVATAR", "ent-1", "user@test.com");

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(objectStorageService);
    }

    @Test
    void list_validKey_returnsMappedList() {
        StoredObject obj1 = object("id-1", "avatar/id-1.png");
        StoredObject obj2 = object("id-2", "avatar/id-2.png");
        when(objectStorageService.list("AVATAR", "ent-1", "user@test.com")).thenReturn(List.of(obj1, obj2));
        when(objectStorageService.presignedUrl(any(StoredObject.class))).thenReturn("https://signed-url");

        ResponseEntity<List<InternalObjectController.ObjectMetadataResponse>> resp =
                controller.list("secret-key", "AVATAR", "ent-1", "user@test.com");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(2);
        assertThat(resp.getBody().get(0).id()).isEqualTo("id-1");
        assertThat(resp.getBody().get(1).id()).isEqualTo("id-2");
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_nullServiceKey_returns401() {
        ResponseEntity<Void> resp = controller.delete(null, "id-1", "user@test.com");

        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(objectStorageService);
    }

    @Test
    void delete_validKey_success_returns204() {
        ResponseEntity<Void> resp = controller.delete("secret-key", "id-1", "user@test.com");

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
        verify(objectStorageService).delete("id-1", "user@test.com");
    }

    @Test
    void delete_validKey_notFound_returns404() {
        doThrow(new NoSuchElementException("not found")).when(objectStorageService).delete("missing", "user@test.com");

        ResponseEntity<Void> resp = controller.delete("secret-key", "missing", "user@test.com");

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void delete_validKey_wrongOwner_returns403() {
        doThrow(new SecurityException("not the owner")).when(objectStorageService).delete("id-1", "attacker@test.com");

        ResponseEntity<Void> resp = controller.delete("secret-key", "id-1", "attacker@test.com");

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }
}
