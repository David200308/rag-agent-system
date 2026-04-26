package com.ragagent.skill;

import com.ragagent.skill.entity.Skill;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
@Tag(name = "Skills", description = "Agent skill (context document) management")
public class SkillController {

    private final SkillService skillService;

    @GetMapping
    @Operation(summary = "List skills owned by the authenticated user")
    public ResponseEntity<List<Skill>> list(HttpServletRequest req) {
        return ResponseEntity.ok(skillService.list(resolveEmail(req)));
    }

    @PostMapping
    @Operation(summary = "Create a skill from extracted text content")
    public ResponseEntity<Skill> create(
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {

        String name     = (String) body.get("name");
        String fileName = (String) body.getOrDefault("fileName", name);
        String fileType = (String) body.getOrDefault("fileType", "txt");
        long   size     = body.get("size") instanceof Number n ? n.longValue() : 0L;
        String content  = (String) body.get("content");

        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Skill created = skillService.create(resolveEmail(req), name, fileName, fileType, size, content);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "Get raw text content of a skill")
    public ResponseEntity<String> getContent(@PathVariable String id) {
        return skillService.getContent(id)
                .map(c -> ResponseEntity.ok().header("Content-Type", "text/plain").body(c))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a skill (owner only)")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest req) {
        try {
            skillService.delete(id, resolveEmail(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    private String resolveEmail(HttpServletRequest req) {
        String email = (String) req.getAttribute("authenticatedEmail");
        return email != null ? email : "anonymous";
    }
}
