package com.agentsystem.model;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Model configuration management.
 *
 * GET  /api/v1/models       — list enabled models (for users to choose from)
 * GET  /api/v1/models/all   — list all models including disabled (admin)
 * POST /api/v1/models       — create a model config
 * PUT  /api/v1/models/{displayName} — update a model config
 * DELETE /api/v1/models/{displayName} — delete a model config
 */
@RestController
@RequestMapping("/api/v1/models")
@Tag(name = "Model Configuration", description = "Manage selectable LLM model configurations")
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService service;

    @GetMapping
    @Operation(summary = "List all enabled models available for selection")
    public ResponseEntity<List<ModelConfig>> listEnabled() {
        return ResponseEntity.ok(service.listEnabled());
    }

    @GetMapping("/all")
    @Operation(summary = "List all model configurations including disabled ones")
    public ResponseEntity<List<ModelConfig>> listAll() {
        return ResponseEntity.ok(service.listAll());
    }

    @PostMapping
    @Operation(summary = "Create a new model configuration")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String displayName = (String) body.get("displayName");
        String platform    = (String) body.get("platform");
        String modelId     = (String) body.get("modelId");

        if (displayName == null || displayName.isBlank()
                || platform == null || platform.isBlank()
                || modelId == null || modelId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "displayName, platform, and modelId are required"));
        }
        try {
            return ResponseEntity.ok(service.create(displayName.trim(), platform.trim(), modelId.trim()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{displayName}")
    @Operation(summary = "Update an existing model configuration")
    public ResponseEntity<?> update(@PathVariable String displayName,
                                    @RequestBody Map<String, Object> body) {
        String platform = (String) body.get("platform");
        String modelId  = (String) body.get("modelId");
        boolean enabled = body.get("enabled") instanceof Boolean b ? b : true;

        if (platform == null || platform.isBlank() || modelId == null || modelId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "platform and modelId are required"));
        }
        try {
            return ResponseEntity.ok(service.update(displayName, platform.trim(), modelId.trim(), enabled));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{displayName}")
    @Operation(summary = "Delete a model configuration")
    public ResponseEntity<Void> delete(@PathVariable String displayName) {
        try {
            service.delete(displayName);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
