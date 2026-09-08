package com.agentsystem.travel.controller;

import com.agentsystem.travel.TravelInnerClient;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Thin proxy over travel-inner's internal API. Resolves ownerUuid (from the JWT, via
 * AuthFilter's request attribute) locally, then forwards to {@link TravelInnerClient}.
 */
@RestController
@RequestMapping("/api/v1/travel")
@RequiredArgsConstructor
@Tag(name = "Travel", description = "Travel records management")
public class TravelController {

    private final TravelInnerClient client;

    @GetMapping
    public ResponseEntity<List<Object>> list(HttpServletRequest req) {
        return ResponseEntity.ok(client.list(ownerUuid(req)));
    }

    @GetMapping("/{id}/expenses")
    public ResponseEntity<List<Object>> expenses(
            @PathVariable String id, HttpServletRequest req) {
        try {
            return ResponseEntity.ok(client.getExpenses(id, ownerUuid(req)));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<Object> create(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(client.create(ownerUuid(req), body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(client.update(id, ownerUuid(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest req) {
        try {
            client.delete(id, ownerUuid(req));
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
