package com.agentsystem.travel.controller;

import com.agentsystem.travel.service.TravelService;

import com.agentsystem.travel.dto.TravelRecordDto;
import com.agentsystem.travel.entity.TravelRecord;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/travel")
@RequiredArgsConstructor
@Tag(name = "Travel", description = "Travel records management")
public class TravelController {

    private final TravelService service;

    @GetMapping
    public ResponseEntity<List<TravelRecordDto>> list(HttpServletRequest req) {
        return ResponseEntity.ok(service.list(ownerUuid(req)));
    }

    @PostMapping
    public ResponseEntity<TravelRecord> create(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(service.create(ownerUuid(req), body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TravelRecord> update(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(service.update(id, ownerUuid(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest req) {
        try {
            service.delete(id, ownerUuid(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    private String ownerUuid(HttpServletRequest req) {
        String uuid = (String) req.getAttribute("authenticatedUserUuid");
        return uuid != null ? uuid : "anonymous";
    }
}
