package com.agentsystem.skill.controller;

import com.agentsystem.skill.service.SkillService;

import com.agentsystem.org.OrgContext;
import com.agentsystem.skill.entity.Skill;
import com.agentsystem.skill.entity.SkillVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
@Tag(name = "Skills", description = "Agent skill (context document) management, with version history")
public class SkillController {

    private final SkillService skillService;

    @GetMapping
    @Operation(summary = "List skills (latest-version metadata) for the authenticated user or org")
    public ResponseEntity<List<SkillService.SkillSummary>> list(HttpServletRequest req) {
        return ResponseEntity.ok(skillService.list(OrgContext.from(req)));
    }

    @PostMapping(consumes = "multipart/form-data")
    @Operation(summary = "Create a skill from an uploaded file (becomes version 1)")
    public ResponseEntity<?> create(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "fileType", required = false) String fileType,
            HttpServletRequest req) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "file is required"));
        }
        try {
            Skill created = skillService.create(
                    OrgContext.from(req), name, file, defaultFileType(file, fileType));
            return ResponseEntity.status(201).body(created);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Could not read uploaded file"));
        }
    }

    @PostMapping(value = "/{id}/versions", consumes = "multipart/form-data")
    @Operation(summary = "Upload a new version of an existing skill (owner/org member only)")
    public ResponseEntity<?> addVersion(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileType", required = false) String fileType,
            HttpServletRequest req) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "file is required"));
        }
        try {
            SkillVersion version = skillService.addVersion(
                    OrgContext.from(req), id, file, defaultFileType(file, fileType));
            return ResponseEntity.status(201).body(version);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Could not read uploaded file"));
        }
    }

    @GetMapping("/{id}/versions")
    @Operation(summary = "List version history for a skill (newest first)")
    public ResponseEntity<List<SkillVersion>> listVersions(@PathVariable String id) {
        return ResponseEntity.ok(skillService.listVersions(id));
    }

    @GetMapping("/{id}/versions/{versionNumber}/content")
    @Operation(summary = "Get raw text content of a specific version")
    public ResponseEntity<String> getVersionContent(@PathVariable String id, @PathVariable int versionNumber) {
        return skillService.getVersionContent(id, versionNumber)
                .map(c -> ResponseEntity.ok().header("Content-Type", "text/plain").body(c))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "Get raw text content of a skill's active (latest approved) version")
    public ResponseEntity<String> getContent(@PathVariable String id) {
        return skillService.getContent(id)
                .map(c -> ResponseEntity.ok().header("Content-Type", "text/plain").body(c))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a skill and all its versions (owner or org member only)")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest req) {
        try {
            skillService.delete(id, OrgContext.from(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    private String defaultFileType(MultipartFile file, String provided) {
        if (provided != null && !provided.isBlank()) return provided;
        String filename = file.getOriginalFilename();
        int dot = filename != null ? filename.lastIndexOf('.') : -1;
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "txt";
    }
}
