package com.ragagent.org;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * In-app team management — authenticated by JWT, scoped to the caller's org.
 * Only accessible in TEAM mode (orgId must be present in the JWT).
 * Only OWNER role can mutate membership or transfer ownership.
 *
 * GET    /api/v1/team/members                    list members of my org
 * POST   /api/v1/team/members                    add a member (owner only)
 * DELETE /api/v1/team/members/{email}            remove a member (owner only)
 * POST   /api/v1/team/transfer-owner             transfer ownership (owner only)
 */
@RestController
@RequestMapping("/api/v1/team")
@RequiredArgsConstructor
@Tag(name = "Team", description = "In-app team member management (team mode only)")
public class TeamController {

    private final OrganizationService service;

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

    private ResponseEntity<Map<String, String>> teamModeRequired() {
        return ResponseEntity.status(403).body(Map.of("error", "This endpoint is only available in team mode."));
    }

    private ResponseEntity<Map<String, String>> bad(String msg) {
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }
}
