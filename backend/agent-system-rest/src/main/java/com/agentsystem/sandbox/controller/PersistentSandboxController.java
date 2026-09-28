package com.agentsystem.sandbox.controller;

import com.agentsystem.org.OrgContext;
import com.agentsystem.sandbox.entity.PersistentSandbox;
import com.agentsystem.sandbox.service.PersistentSandboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for user-managed, permanent sandboxes (Sandbox Management page).
 * Every sandbox here is owned by exactly one user — only that user can launch,
 * stop, restart, clear or remove it.
 */
@RestController
@RequestMapping("/api/v1/sandboxes")
@RequiredArgsConstructor
@Tag(name = "Sandbox Management", description = "Permanent, user-owned sandbox lifecycle endpoints")
public class PersistentSandboxController {

    private final PersistentSandboxService sandboxService;

    @GetMapping
    @Operation(summary = "List the caller's persistent sandboxes")
    public ResponseEntity<List<PersistentSandbox>> list(HttpServletRequest req) {
        return ResponseEntity.ok(sandboxService.list(OrgContext.from(req).userUuid()));
    }

    @GetMapping("/quota")
    @Operation(summary = "Caller's persistent sandbox quota usage")
    public ResponseEntity<PersistentSandboxService.Quota> quota(HttpServletRequest req) {
        return ResponseEntity.ok(sandboxService.quota(OrgContext.from(req).userUuid()));
    }

    @PostMapping
    @Operation(summary = "Launch a new persistent sandbox")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        String name = (String) body.get("name");
        boolean network = Boolean.TRUE.equals(body.get("network"));
        try {
            return ResponseEntity.ok(sandboxService.create(OrgContext.from(req).userUuid(), name, network));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/stop")
    @Operation(summary = "Stop (docker-stop) a running sandbox")
    public ResponseEntity<?> stop(@PathVariable String id, HttpServletRequest req) {
        return guarded(() -> sandboxService.stop(id, OrgContext.from(req).userUuid()));
    }

    @PostMapping("/{id}/restart")
    @Operation(summary = "Restart a sandbox's container")
    public ResponseEntity<?> restart(@PathVariable String id, HttpServletRequest req) {
        return guarded(() -> sandboxService.restart(id, OrgContext.from(req).userUuid()));
    }

    @PostMapping("/{id}/clear")
    @Operation(summary = "Wipe the sandbox's /workspace (must be running)")
    public ResponseEntity<?> clear(@PathVariable String id, HttpServletRequest req) {
        return guarded(() -> {
            sandboxService.clear(id, OrgContext.from(req).userUuid());
            return null;
        });
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Destroy the sandbox and remove it (detaches from any workflow)")
    public ResponseEntity<?> remove(@PathVariable String id, HttpServletRequest req) {
        return guarded(() -> {
            sandboxService.remove(id, OrgContext.from(req).userUuid());
            return null;
        });
    }

    private ResponseEntity<?> guarded(java.util.function.Supplier<?> action) {
        try {
            Object result = action.get();
            return result != null ? ResponseEntity.ok(result) : ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }
}
