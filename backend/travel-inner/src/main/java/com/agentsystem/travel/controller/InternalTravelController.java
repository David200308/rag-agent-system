package com.agentsystem.travel.controller;

import com.agentsystem.travel.config.TravelServiceProperties;
import com.agentsystem.travel.dto.TravelRecordDto;
import com.agentsystem.travel.dto.TravelRecordSummaryDto;
import com.agentsystem.travel.entity.TravelRecord;
import com.agentsystem.travel.service.TravelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Internal-only API consumed by agent-system-rest's TravelInnerClient.
 * Auth: shared {@code X-Travel-Key} header — NOT a user JWT. ownerUuid is always passed
 * explicitly by the caller (this service never resolves it from a JWT itself, so it stays
 * free of any dependency on the auth/user/org packages).
 */
@RestController
@RequestMapping("/internal/travel")
@RequiredArgsConstructor
public class InternalTravelController {

    private final TravelService           service;
    private final TravelServiceProperties serviceProperties;

    @GetMapping
    public ResponseEntity<List<TravelRecordSummaryDto>> list(
            @RequestHeader(value = "X-Travel-Key", required = false) String key,
            @RequestParam String ownerUuid) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.list(ownerUuid));
    }

    /** Backs TravelAgentTool — trips this owner has explicitly opted into being visible to chat. */
    @GetMapping("/chat-visible")
    public ResponseEntity<List<TravelRecordDto>> listChatVisible(
            @RequestHeader(value = "X-Travel-Key", required = false) String key,
            @RequestParam String ownerUuid) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.listChatVisible(ownerUuid));
    }

    @GetMapping("/{id}/expenses")
    public ResponseEntity<List<Map<String, Object>>> expenses(
            @RequestHeader(value = "X-Travel-Key", required = false) String key,
            @PathVariable String id, @RequestParam String ownerUuid) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        try {
            return ResponseEntity.ok(service.getExpenses(id, ownerUuid));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<TravelRecord> create(
            @RequestHeader(value = "X-Travel-Key", required = false) String key,
            @RequestParam String ownerUuid, @RequestBody Map<String, Object> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        return ResponseEntity.status(201).body(service.create(ownerUuid, body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TravelRecord> update(
            @RequestHeader(value = "X-Travel-Key", required = false) String key,
            @PathVariable String id, @RequestParam String ownerUuid,
            @RequestBody Map<String, Object> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        try {
            return ResponseEntity.ok(service.update(id, ownerUuid, body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "X-Travel-Key", required = false) String key,
            @PathVariable String id, @RequestParam String ownerUuid) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        try {
            service.delete(id, ownerUuid);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    private boolean validKey(String key) {
        return key != null && key.equals(serviceProperties.serviceKey());
    }
}
