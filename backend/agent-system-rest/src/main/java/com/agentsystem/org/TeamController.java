package com.agentsystem.org;

import com.agentsystem.knowledge.KnowledgeSourceService;
import com.agentsystem.skill.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * GET    /api/v1/team/members                           list members
 * POST   /api/v1/team/members                           add member (owner only)
 * DELETE /api/v1/team/members/{email}                   remove member (owner only)
 * POST   /api/v1/team/transfer-owner                    transfer ownership (owner only)
 * GET    /api/v1/team/approvals                         list pending KB + skills (owner only)
 * POST   /api/v1/team/approvals/knowledge/{id}/approve  approve KB source (owner only)
 * POST   /api/v1/team/approvals/knowledge/{id}/reject   reject KB source (owner only)
 * POST   /api/v1/team/approvals/skills/{id}/approve     approve skill (owner only)
 * POST   /api/v1/team/approvals/skills/{id}/reject      reject skill (owner only)
 */
@RestController
@RequestMapping("/api/v1/team")
@RequiredArgsConstructor
@Tag(name = "Team", description = "In-app team member management (team mode only)")
public class TeamController {

    private final OrganizationService    service;
    private final KnowledgeSourceService knowledgeSourceService;
    private final SkillService           skillService;

    @GetMapping("/members")
    @Operation(summary = "List members of the current org")
    public ResponseEntity<?> listMembers(HttpServletRequest req) {
        OrgContext ctx = OrgContext.from(req);
        if (!ctx.isTeam()) return teamModeRequired();
        List<OrgMember> members = service.listMembers(ctx.orgId());
        return ResponseEntity.ok(members);
    }

    @PostMapping("/members")
    @Operation(summary = "Add a member to the current org (owner only)")
    public ResponseEntity<?> addMember(@RequestBody Map<String, String> body,
                                        HttpServletRequest req) {
        OrgContext ctx = OrgContext.from(req);
        if (!ctx.isTeam()) return teamModeRequired();

        String newEmail = body.get("email");
        String roleStr  = body.getOrDefault("role", "MEMBER");
        if (newEmail == null || newEmail.isBlank()) {
            return bad("email is required");
        }
        try {
            service.requireOwner(ctx.orgId(), ctx.email());
            OrgMember.Role role = OrgMember.Role.valueOf(roleStr.toUpperCase());
            OrgMember m = service.addMember(ctx.orgId(), newEmail.trim().toLowerCase(), role);
            return ResponseEntity.status(201).body(m);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    @DeleteMapping("/members/{email}")
    @Operation(summary = "Remove a member from the current org (owner only)")
    public ResponseEntity<?> removeMember(@PathVariable String email,
                                           HttpServletRequest req) {
        OrgContext ctx = OrgContext.from(req);
        if (!ctx.isTeam()) return teamModeRequired();
        try {
            service.requireOwner(ctx.orgId(), ctx.email());
            if (email.equalsIgnoreCase(ctx.email())) {
                return bad("Owner cannot remove themselves. Transfer ownership first.");
            }
            service.removeMember(ctx.orgId(), email.toLowerCase());
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    @PostMapping("/transfer-owner")
    @Operation(summary = "Transfer ownership to another member (owner only)")
    public ResponseEntity<?> transferOwner(@RequestBody Map<String, String> body,
                                            HttpServletRequest req) {
        OrgContext ctx = OrgContext.from(req);
        if (!ctx.isTeam()) return teamModeRequired();

        String newOwner = body.get("email");
        if (newOwner == null || newOwner.isBlank()) return bad("email is required");
        try {
            service.transferOwner(ctx.orgId(), ctx.email(), newOwner.trim().toLowerCase());
            return ResponseEntity.ok(Map.of("message", "Ownership transferred to " + newOwner.trim().toLowerCase()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    // ── Approval queue ────────────────────────────────────────────────────────

    @GetMapping("/approvals")
    @Operation(summary = "List all pending KB and skill submissions (owner only)")
    public ResponseEntity<?> listApprovals(HttpServletRequest req) {
        OrgContext ctx = OrgContext.from(req);
        if (!ctx.isTeam()) return teamModeRequired();
        try {
            service.requireOwner(ctx.orgId(), ctx.email());
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of(
                "knowledge", knowledgeSourceService.listPendingByOrg(ctx.orgId()),
                // "skills" here is a list of pending *versions* (SkillService.PendingSkillVersion) —
                // a skill can have an approved version in use while a newer one awaits review.
                "skills",    skillService.listPendingByOrg(ctx.orgId())
        ));
    }

    @PostMapping("/approvals/knowledge/{id}/approve")
    @Operation(summary = "Approve a pending KB source (owner only)")
    public ResponseEntity<?> approveKnowledge(@PathVariable Long id, HttpServletRequest req) {
        OrgContext ctx = OrgContext.from(req);
        if (!ctx.isTeam()) return teamModeRequired();
        try {
            service.requireOwner(ctx.orgId(), ctx.email());
            return ResponseEntity.ok(knowledgeSourceService.approve(id));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    @PostMapping("/approvals/knowledge/{id}/reject")
    @Operation(summary = "Reject a pending KB source (owner only) — removes from Weaviate")
    public ResponseEntity<?> rejectKnowledge(@PathVariable Long id, HttpServletRequest req) {
        OrgContext ctx = OrgContext.from(req);
        if (!ctx.isTeam()) return teamModeRequired();
        try {
            service.requireOwner(ctx.orgId(), ctx.email());
            knowledgeSourceService.reject(id);
            return ResponseEntity.ok(Map.of("message", "Knowledge source rejected and removed from vector store."));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    @PostMapping("/approvals/skills/{id}/approve")
    @Operation(summary = "Approve a pending skill version (owner only) — {id} is the version id")
    public ResponseEntity<?> approveSkill(@PathVariable String id, HttpServletRequest req) {
        OrgContext ctx = OrgContext.from(req);
        if (!ctx.isTeam()) return teamModeRequired();
        try {
            service.requireOwner(ctx.orgId(), ctx.email());
            return ResponseEntity.ok(skillService.approve(id));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    @PostMapping("/approvals/skills/{id}/reject")
    @Operation(summary = "Reject a pending skill version (owner only) — {id} is the version id")
    public ResponseEntity<?> rejectSkill(@PathVariable String id, HttpServletRequest req) {
        OrgContext ctx = OrgContext.from(req);
        if (!ctx.isTeam()) return teamModeRequired();
        try {
            service.requireOwner(ctx.orgId(), ctx.email());
            skillService.reject(id);
            return ResponseEntity.ok(Map.of("message", "Skill rejected."));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    private ResponseEntity<Map<String, String>> teamModeRequired() {
        return ResponseEntity.status(403).body(Map.of("error", "This endpoint is only available in team mode."));
    }

    private ResponseEntity<Map<String, String>> bad(String msg) {
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }
}
