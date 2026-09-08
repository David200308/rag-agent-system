package com.agentsystem.travel.service.impl;

import com.agentsystem.travel.service.TravelService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentsystem.travel.dto.TravelRecordDto;
import com.agentsystem.travel.dto.TravelRecordSummaryDto;
import com.agentsystem.travel.entity.TravelRecord;
import com.agentsystem.travel.repository.TravelRecordRepository;
import com.agentsystem.travel.repository.TravelRecordSummaryProjection;
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
public class TravelServiceImpl implements TravelService {

    private final TravelRecordRepository repo;
    private final ObjectMapper           mapper;

    @Transactional(readOnly = true)
    @Override
    public List<TravelRecordSummaryDto> list(String ownerUuid) {
        return repo.findSummaryByOwnerUuidOrderByStartDateDesc(ownerUuid).stream()
                .map(this::toSummaryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<TravelRecordDto> listChatVisible(String ownerUuid) {
        return repo.findByOwnerUuidAndAllowChatTrueOrderByStartDateDesc(ownerUuid).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    @Override
    public TravelRecord create(String ownerUuid, Map<String, Object> body) {
        TravelRecord r = new TravelRecord();
        r.setId(UUID.randomUUID().toString());
        r.setOwnerUuid(ownerUuid);
        applyFields(r, body);
        return repo.save(r);
    }

    @Transactional
    @Override
    public TravelRecord update(String id, String ownerUuid, Map<String, Object> body) {
        TravelRecord r = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        if (!r.getOwnerUuid().equals(ownerUuid)) throw new SecurityException("Forbidden");
        applyFields(r, body);
        r.setUpdatedAt(Instant.now());
        return repo.save(r);
    }

    @Transactional
    @Override
    public void delete(String id, String ownerUuid) {
        repo.findById(id).ifPresent(r -> {
            if (!r.getOwnerUuid().equals(ownerUuid)) throw new SecurityException("Forbidden");
            repo.delete(r);
        });
    }

    @Transactional(readOnly = true)
    @Override
    public List<Map<String, Object>> getExpenses(String id, String ownerUuid) {
        TravelRecord r = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        if (!r.getOwnerUuid().equals(ownerUuid)) throw new SecurityException("Forbidden");
        return parseJsonList(r.getExpensesJson(), "expenses", id);
    }

    private void applyFields(TravelRecord r, Map<String, Object> body) {
        if (body.containsKey("title"))     r.setTitle(str(body, "title"));
        if (body.containsKey("startDate")) r.setStartDate(str(body, "startDate"));
        if (body.containsKey("endDate"))   r.setEndDate(str(body, "endDate"));
        if (body.containsKey("notes"))     r.setNotes(str(body, "notes"));
        if (body.containsKey("allowChat")) r.setAllowChat(Boolean.TRUE.equals(body.get("allowChat")));
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
        List<Map<String, Object>> stops = parseJsonList(r.getStopsJson(), "stops", r.getId());
        List<Map<String, Object>> expenses = parseJsonList(r.getExpensesJson(), "expenses", r.getId());
        return new TravelRecordDto(
                r.getId(), r.getOwnerUuid(), r.getTitle(),
                r.getStartDate(), r.getEndDate(),
                stops, expenses, r.getNotes(), r.isAllowChat(),
                r.getCreatedAt(), r.getUpdatedAt()
        );
    }

    private TravelRecordSummaryDto toSummaryDto(TravelRecordSummaryProjection p) {
        List<Map<String, Object>> stops = parseJsonList(p.getStopsJson(), "stops", p.getId());
        return new TravelRecordSummaryDto(
                p.getId(), p.getOwnerUuid(), p.getTitle(),
                p.getStartDate(), p.getEndDate(),
                stops, p.getNotes(), p.isAllowChat(),
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }

    private List<Map<String, Object>> parseJsonList(String json, String fieldName, String recordId) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse {} for record {}", fieldName, recordId, e);
            return Collections.emptyList();
        }
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null ? v.toString() : null;
    }
}
