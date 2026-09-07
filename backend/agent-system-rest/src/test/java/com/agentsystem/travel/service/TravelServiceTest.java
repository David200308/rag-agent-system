package com.agentsystem.travel.service;

import com.agentsystem.travel.service.impl.TravelServiceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentsystem.travel.dto.TravelRecordDto;
import com.agentsystem.travel.dto.TravelRecordSummaryDto;
import com.agentsystem.travel.entity.TravelRecord;
import com.agentsystem.travel.repository.TravelRecordRepository;
import com.agentsystem.travel.repository.TravelRecordSummaryProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TravelServiceTest {

    @Mock TravelRecordRepository repo;

    TravelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TravelServiceImpl(repo, new ObjectMapper());
    }

    private TravelRecord makeRecord(String id, String email) {
        TravelRecord r = new TravelRecord();
        r.setId(id);
        r.setOwnerUuid(email);
        r.setTitle("Trip to Paris");
        r.setStartDate("2025-06-01");
        r.setEndDate("2025-06-10");
        r.setCreatedAt(Instant.now());
        r.setUpdatedAt(Instant.now());
        return r;
    }

    private TravelRecordSummaryProjection projection(String id, String email, String title, String stopsJson) {
        return new TravelRecordSummaryProjection() {
            public String getId() { return id; }
            public String getOwnerUuid() { return email; }
            public String getTitle() { return title; }
            public String getStartDate() { return "2025-06-01"; }
            public String getEndDate() { return "2025-06-10"; }
            public String getStopsJson() { return stopsJson; }
            public String getNotes() { return null; }
            public boolean isAllowChat() { return false; }
            public Instant getCreatedAt() { return Instant.now(); }
            public Instant getUpdatedAt() { return Instant.now(); }
        };
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_returnsAllRecordsForEmail() {
        when(repo.findSummaryByOwnerUuidOrderByStartDateDesc("user@test.com"))
                .thenReturn(List.of(projection("id-1", "user@test.com", "Trip to Paris", null)));

        List<TravelRecordSummaryDto> result = service.list("user@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("id-1");
        assertThat(result.get(0).ownerUuid()).isEqualTo("user@test.com");
        assertThat(result.get(0).title()).isEqualTo("Trip to Paris");
    }

    @Test
    void list_noRecords_returnsEmpty() {
        when(repo.findSummaryByOwnerUuidOrderByStartDateDesc("other@test.com")).thenReturn(List.of());

        assertThat(service.list("other@test.com")).isEmpty();
    }

    @Test
    void list_recordWithStopsJson_parsesStops() {
        when(repo.findSummaryByOwnerUuidOrderByStartDateDesc("user@test.com"))
                .thenReturn(List.of(projection("id-2", "user@test.com", "Trip",
                        "[{\"city\":\"Paris\",\"days\":3}]")));

        List<TravelRecordSummaryDto> result = service.list("user@test.com");

        assertThat(result.get(0).stops()).hasSize(1);
        assertThat(result.get(0).stops().get(0)).containsEntry("city", "Paris");
    }

    @Test
    void list_recordWithInvalidStopsJson_returnsEmptyStops() {
        when(repo.findSummaryByOwnerUuidOrderByStartDateDesc("user@test.com"))
                .thenReturn(List.of(projection("id-3", "user@test.com", "Trip", "not-valid-json")));

        List<TravelRecordSummaryDto> result = service.list("user@test.com");

        assertThat(result.get(0).stops()).isEmpty();
    }

    @Test
    void list_recordWithBlankStopsJson_returnsEmptyStops() {
        when(repo.findSummaryByOwnerUuidOrderByStartDateDesc("user@test.com"))
                .thenReturn(List.of(projection("id-4", "user@test.com", "Trip", "   ")));

        List<TravelRecordSummaryDto> result = service.list("user@test.com");

        assertThat(result.get(0).stops()).isEmpty();
    }

    // ── getExpenses ───────────────────────────────────────────────────────────

    @Test
    void getExpenses_ownerMatch_parsesExpenses() {
        TravelRecord r = makeRecord("id-5", "user@test.com");
        r.setExpensesJson("[{\"category\":\"Flight\",\"amount\":1200,\"currency\":\"USD\"}]");
        when(repo.findById("id-5")).thenReturn(Optional.of(r));

        List<Map<String, Object>> result = service.getExpenses("id-5", "user@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("category", "Flight");
    }

    @Test
    void getExpenses_invalidJson_returnsEmpty() {
        TravelRecord r = makeRecord("id-6", "user@test.com");
        r.setExpensesJson("not-valid-json");
        when(repo.findById("id-6")).thenReturn(Optional.of(r));

        assertThat(service.getExpenses("id-6", "user@test.com")).isEmpty();
    }

    @Test
    void getExpenses_notFound_throwsIllegalArgument() {
        when(repo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getExpenses("missing", "user@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not found");
    }

    @Test
    void getExpenses_wrongOwner_throwsSecurityException() {
        TravelRecord r = makeRecord("id-7x", "owner@test.com");
        when(repo.findById("id-7x")).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.getExpenses("id-7x", "other@test.com"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Forbidden");
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_savesRecordWithCorrectOwner() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> body = Map.of("title", "Japan Trip", "startDate", "2025-04-01", "endDate", "2025-04-14");
        TravelRecord result = service.create("user@test.com", body);

        assertThat(result.getOwnerUuid()).isEqualTo("user@test.com");
        assertThat(result.getTitle()).isEqualTo("Japan Trip");
        assertThat(result.getId()).isNotBlank();
        verify(repo).save(result);
    }

    @Test
    void create_withNotes_setsNotes() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> body = Map.of("title", "UK Trip", "notes", "Visit London");
        TravelRecord result = service.create("user@test.com", body);

        assertThat(result.getNotes()).isEqualTo("Visit London");
    }

    @Test
    void create_withStops_serializes() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        List<Map<String, Object>> stops = List.of(Map.of("city", "Tokyo"));
        Map<String, Object> body = Map.of("title", "Japan", "stops", stops);
        TravelRecord result = service.create("user@test.com", body);

        assertThat(result.getStopsJson()).contains("Tokyo");
    }

    @Test
    void create_withExpenses_serializes() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        List<Map<String, Object>> expenses = List.of(Map.of("category", "Flight", "amount", 1200, "currency", "USD"));
        Map<String, Object> body = Map.of("title", "Japan", "expenses", expenses);
        TravelRecord result = service.create("user@test.com", body);

        assertThat(result.getExpensesJson()).contains("Flight");
    }

    // ── listChatVisible ──────────────────────────────────────────────────────

    @Test
    void listChatVisible_returnsOnlyChatEnabledRecords() {
        TravelRecord r = makeRecord("id-7", "user@test.com");
        r.setAllowChat(true);
        when(repo.findByOwnerUuidAndAllowChatTrueOrderByStartDateDesc("user@test.com")).thenReturn(List.of(r));

        List<TravelRecordDto> result = service.listChatVisible("user@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).allowChat()).isTrue();
    }

    @Test
    void listChatVisible_noneEnabled_returnsEmpty() {
        when(repo.findByOwnerUuidAndAllowChatTrueOrderByStartDateDesc("user@test.com")).thenReturn(List.of());

        assertThat(service.listChatVisible("user@test.com")).isEmpty();
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_withAllowChat_togglesFlag() {
        TravelRecord existing = makeRecord("rec-5", "user@test.com");
        when(repo.findById("rec-5")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        TravelRecord result = service.update("rec-5", "user@test.com", Map.of("allowChat", true));

        assertThat(result.isAllowChat()).isTrue();
    }


    @Test
    void update_ownerMatch_updatesAndSaves() {
        TravelRecord existing = makeRecord("rec-1", "user@test.com");
        when(repo.findById("rec-1")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> body = Map.of("title", "Updated Title");
        TravelRecord result = service.update("rec-1", "user@test.com", body);

        assertThat(result.getTitle()).isEqualTo("Updated Title");
        verify(repo).save(existing);
    }

    @Test
    void update_notFound_throwsIllegalArgument() {
        when(repo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("missing", "user@test.com", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not found");
    }

    @Test
    void update_wrongOwner_throwsSecurityException() {
        TravelRecord existing = makeRecord("rec-2", "owner@test.com");
        when(repo.findById("rec-2")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update("rec-2", "other@test.com", Map.of()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Forbidden");
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_ownerMatch_deletesRecord() {
        TravelRecord existing = makeRecord("rec-3", "user@test.com");
        when(repo.findById("rec-3")).thenReturn(Optional.of(existing));

        service.delete("rec-3", "user@test.com");

        verify(repo).delete(existing);
    }

    @Test
    void delete_notFound_doesNothing() {
        when(repo.findById("not-found")).thenReturn(Optional.empty());

        service.delete("not-found", "user@test.com");

        verify(repo, never()).delete(any());
    }

    @Test
    void delete_wrongOwner_throwsSecurityException() {
        TravelRecord existing = makeRecord("rec-4", "owner@test.com");
        when(repo.findById("rec-4")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.delete("rec-4", "other@test.com"))
                .isInstanceOf(SecurityException.class);
    }
}
