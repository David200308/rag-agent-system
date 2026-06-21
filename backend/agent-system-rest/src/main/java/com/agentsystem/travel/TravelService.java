package com.agentsystem.travel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentsystem.travel.dto.TravelRecordDto;
import com.agentsystem.travel.entity.TravelRecord;
import com.agentsystem.travel.repository.TravelRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TravelService {

    private final TravelRecordRepository repo;
    private final ObjectMapper           mapper;

    @Transactional(readOnly = true)
    public List<TravelRecordDto> list(String ownerEmail) {
        return repo.findByOwnerEmailOrderByStartDateDesc(ownerEmail).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public TravelRecord create(String ownerEmail, Map<String, Object> body) {
        TravelRecord r = new TravelRecord();
        r.setId(UUID.randomUUID().toString());
        r.setOwnerEmail(ownerEmail);
        applyFields(r, body);
        return repo.save(r);
    }

    @Transactional
    public TravelRecord update(String id, String ownerEmail, Map<String, Object> body) {
        TravelRecord r = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        if (!r.getOwnerEmail().equals(ownerEmail)) throw new SecurityException("Forbidden");
        applyFields(r, body);
        r.setUpdatedAt(Instant.now());
        return repo.save(r);
    }

    @Transactional
    public void delete(String id, String ownerEmail) {
        repo.findById(id).ifPresent(r -> {
            if (!r.getOwnerEmail().equals(ownerEmail)) throw new SecurityException("Forbidden");
            repo.delete(r);
        });
    }

    private void applyFields(TravelRecord r, Map<String, Object> body) {
        if (body.containsKey("title"))     r.setTitle(str(body, "title"));
        if (body.containsKey("startDate")) r.setStartDate(str(body, "startDate"));
        if (body.containsKey("endDate"))   r.setEndDate(str(body, "endDate"));
        if (body.containsKey("notes"))     r.setNotes(str(body, "notes"));
        if (body.containsKey("stops")) {
            try {
                Object raw = body.get("stops");
                String json = mapper.writeValueAsString(raw);
                r.setStopsJson(json);
            } catch (Exception e) {
                log.warn("Failed to serialize stops", e);
                r.setStopsJson("[]");
            }
        }
        if (body.containsKey("expenses")) {
            try {
                Object raw = body.get("expenses");
                String json = mapper.writeValueAsString(raw);
                r.setExpensesJson(json);
            } catch (Exception e) {
                log.warn("Failed to serialize expenses", e);
                r.setExpensesJson("[]");
            }
        }
    }

    private TravelRecordDto toDto(TravelRecord r) {
        List<Map<String, Object>> stops = Collections.emptyList();
        if (r.getStopsJson() != null && !r.getStopsJson().isBlank()) {
            try {
                stops = mapper.readValue(r.getStopsJson(),
                        new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse stops for record {}", r.getId(), e);
            }
        }
        List<Map<String, Object>> expenses = Collections.emptyList();
        if (r.getExpensesJson() != null && !r.getExpensesJson().isBlank()) {
            try {
                expenses = mapper.readValue(r.getExpensesJson(),
                        new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse expenses for record {}", r.getId(), e);
            }
        }
        return new TravelRecordDto(
                r.getId(), r.getOwnerEmail(), r.getTitle(),
                r.getStartDate(), r.getEndDate(),
                stops, expenses, r.getNotes(),
                r.getCreatedAt(), r.getUpdatedAt()
        );
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null ? v.toString() : null;
    }
}
