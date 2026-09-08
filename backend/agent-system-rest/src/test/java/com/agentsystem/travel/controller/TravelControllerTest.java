package com.agentsystem.travel.controller;

import com.agentsystem.travel.TravelInnerClient;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TravelControllerTest {

    @Mock TravelInnerClient client;
    @Mock HttpServletRequest request;
    @InjectMocks TravelController controller;

    private void stubUuid(String uuid) {
        when(request.getAttribute("authenticatedUserUuid")).thenReturn(uuid);
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_returnsOkWithRecords() {
        stubUuid("user@test.com");
        Map<String, Object> record = Map.of("id", "id-1", "title", "Trip");
        when(client.list("user@test.com")).thenReturn(List.of(record));

        ResponseEntity<List<Object>> resp = controller.list(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0)).isEqualTo(record);
    }

    @Test
    void list_noUuid_usesAnonymous() {
        when(request.getAttribute("authenticatedUserUuid")).thenReturn(null);
        when(client.list("anonymous")).thenReturn(List.of());

        ResponseEntity<List<Object>> resp = controller.list(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEmpty();
        verify(client).list("anonymous");
    }

    // ── expenses ──────────────────────────────────────────────────────────────

    @Test
    void expenses_ownerMatch_returnsList() {
        stubUuid("user@test.com");
        List<Object> expenses = List.of(Map.of("category", "Flight"));
        when(client.getExpenses("id-1", "user@test.com")).thenReturn(expenses);

        ResponseEntity<List<Object>> resp = controller.expenses("id-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEqualTo(expenses);
    }

    @Test
    void expenses_wrongOwner_returns403() {
        stubUuid("other@test.com");
        when(client.getExpenses("id-1", "other@test.com")).thenThrow(new SecurityException("Forbidden"));

        ResponseEntity<List<Object>> resp = controller.expenses("id-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void expenses_notFound_returns404() {
        stubUuid("user@test.com");
        when(client.getExpenses("missing", "user@test.com")).thenThrow(new IllegalArgumentException("Not found"));

        ResponseEntity<List<Object>> resp = controller.expenses("missing", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_returns201WithRecord() {
        stubUuid("user@test.com");
        Map<String, Object> saved = Map.of("id", "new-id", "title", "Japan Trip");
        Map<String, Object> body = Map.of("title", "Japan Trip");
        when(client.create("user@test.com", body)).thenReturn(saved);

        ResponseEntity<Object> resp = controller.create(body, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody()).isEqualTo(saved);
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_ownerMatch_returns200() {
        stubUuid("user@test.com");
        Map<String, Object> updated = Map.of("id", "id-1", "title", "Updated");
        Map<String, Object> body = Map.of("title", "Updated");
        when(client.update("id-1", "user@test.com", body)).thenReturn(updated);

        ResponseEntity<Object> resp = controller.update("id-1", body, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEqualTo(updated);
    }

    @Test
    void update_wrongOwner_returns403() {
        stubUuid("other@test.com");
        Map<String, Object> body = Map.of("title", "X");
        when(client.update("id-1", "other@test.com", body))
                .thenThrow(new SecurityException("Forbidden"));

        ResponseEntity<Object> resp = controller.update("id-1", body, request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_ownerMatch_returns204() {
        stubUuid("user@test.com");

        ResponseEntity<Void> resp = controller.delete("id-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
        verify(client).delete("id-1", "user@test.com");
    }

    @Test
    void delete_wrongOwner_returns403() {
        stubUuid("other@test.com");
        doThrow(new SecurityException("Forbidden")).when(client).delete("id-1", "other@test.com");

        ResponseEntity<Void> resp = controller.delete("id-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }
}
