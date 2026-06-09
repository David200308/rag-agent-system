package com.ragagent.travel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.travel.dto.TravelRecordDto;
import com.ragagent.travel.entity.TravelRecord;
import com.ragagent.travel.repository.TravelRecordRepository;
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

    TravelService service;

    @BeforeEach
    void setUp() {
        service = new TravelService(repo, new ObjectMapper());
    }

    private TravelRecord makeRecord(String id, String email) {
        TravelRecord r = new TravelRecord();
        r.setId(id);
        r.setOwnerEmail(email);
        r.setTitle("Trip to Paris");
        r.setStartDate("2025-06-01");
        r.setEndDate("2025-06-10");
        r.setCreatedAt(Instant.now());
        r.setUpdatedAt(Instant.now());
        return r;
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_returnsAllRecordsForEmail() {
        TravelRecord r = makeRecord("id-1", "user@test.com");
        when(repo.findByOwnerEmailOrderByStartDateDesc("user@test.com")).thenReturn(List.of(r));

        List<TravelRecordDto> result = service.list("user@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("id-1");
        assertThat(result.get(0).ownerEmail()).isEqualTo("user@test.com");
        assertThat(result.get(0).title()).isEqualTo("Trip to Paris");
    }

    @Test
    void list_noRecords_returnsEmpty() {
        when(repo.findByOwnerEmailOrderByStartDateDesc("other@test.com")).thenReturn(List.of());

        assertThat(service.list("other@test.com")).isEmpty();
    }

    @Test
    void list_recordWithStopsJson_parsesStops() {
        TravelRecord r = makeRecord("id-2", "user@test.com");
        r.setStopsJson("[{\"city\":\"Paris\",\"days\":3}]");
        when(repo.findByOwnerEmailOrderByStartDateDesc("user@test.com")).thenReturn(List.of(r));

        List<TravelRecordDto> result = service.list("user@test.com");

        assertThat(result.get(0).stops()).hasSize(1);
        assertThat(result.get(0).stops().get(0)).containsEntry("city", "Paris");
    }

    @Test
    void list_recordWithInvalidStopsJson_returnsEmptyStops() {
        TravelRecord r = makeRecord("id-3", "user@test.com");
        r.setStopsJson("not-valid-json");
        when(repo.findByOwnerEmailOrderByStartDateDesc("user@test.com")).thenReturn(List.of(r));

        List<TravelRecordDto> result = service.list("user@test.com");

        assertThat(result.get(0).stops()).isEmpty();
    }

    @Test
    void list_recordWithBlankStopsJson_returnsEmptyStops() {
        TravelRecord r = makeRecord("id-4", "user@test.com");
        r.setStopsJson("   ");
        when(repo.findByOwnerEmailOrderByStartDateDesc("user@test.com")).thenReturn(List.of(r));

        List<TravelRecordDto> result = service.list("user@test.com");

        assertThat(result.get(0).stops()).isEmpty();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_savesRecordWithCorrectOwner() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> body = Map.of("title", "Japan Trip", "startDate", "2025-04-01", "endDate", "2025-04-14");
        TravelRecord result = service.create("user@test.com", body);

        assertThat(result.getOwnerEmail()).isEqualTo("user@test.com");
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

    // ── update ────────────────────────────────────────────────────────────────

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
