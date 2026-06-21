package com.ragagent.travel;

import com.ragagent.travel.dto.TravelRecordDto;
import com.ragagent.travel.entity.TravelRecord;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TravelControllerTest {

    @Mock TravelService service;
    @Mock HttpServletRequest request;
    @InjectMocks TravelController controller;

    private void stubEmail(String email) {
        when(request.getAttribute("authenticatedEmail")).thenReturn(email);
    }

    private TravelRecord record(String id, String email) {
        TravelRecord r = new TravelRecord();
        r.setId(id);
        r.setOwnerEmail(email);
        r.setTitle("Trip");
        r.setCreatedAt(Instant.now());
        r.setUpdatedAt(Instant.now());
        return r;
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_returnsOkWithRecords() {
        stubEmail("user@test.com");
        TravelRecordDto dto = new TravelRecordDto("id-1", "user@test.com", "Trip", null, null, null, null, null, Instant.now(), Instant.now());
        when(service.list("user@test.com")).thenReturn(List.of(dto));

        ResponseEntity<List<TravelRecordDto>> resp = controller.list(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).id()).isEqualTo("id-1");
    }

    @Test
    void list_noEmail_usesAnonymous() {
        when(request.getAttribute("authenticatedEmail")).thenReturn(null);
        when(service.list("anonymous")).thenReturn(List.of());

        ResponseEntity<List<TravelRecordDto>> resp = controller.list(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEmpty();
        verify(service).list("anonymous");
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_returns201WithRecord() {
        stubEmail("user@test.com");
        TravelRecord saved = record("new-id", "user@test.com");
        Map<String, Object> body = Map.of("title", "Japan Trip");
        when(service.create("user@test.com", body)).thenReturn(saved);

        ResponseEntity<TravelRecord> resp = controller.create(body, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getId()).isEqualTo("new-id");
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_ownerMatch_returns200() {
        stubEmail("user@test.com");
        TravelRecord updated = record("id-1", "user@test.com");
        updated.setTitle("Updated");
        Map<String, Object> body = Map.of("title", "Updated");
        when(service.update("id-1", "user@test.com", body)).thenReturn(updated);

        ResponseEntity<TravelRecord> resp = controller.update("id-1", body, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().getTitle()).isEqualTo("Updated");
    }

    @Test
    void update_wrongOwner_returns403() {
        stubEmail("other@test.com");
        Map<String, Object> body = Map.of("title", "X");
        when(service.update("id-1", "other@test.com", body))
                .thenThrow(new SecurityException("Forbidden"));

        ResponseEntity<TravelRecord> resp = controller.update("id-1", body, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_ownerMatch_returns204() {
        stubEmail("user@test.com");

        ResponseEntity<Void> resp = controller.delete("id-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
        verify(service).delete("id-1", "user@test.com");
    }

    @Test
    void delete_wrongOwner_returns403() {
        stubEmail("other@test.com");
        doThrow(new SecurityException("Forbidden")).when(service).delete("id-1", "other@test.com");

        ResponseEntity<Void> resp = controller.delete("id-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }
}
