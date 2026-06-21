package com.agentsystem.org;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin-only endpoints for managing organizations and their members.
 * Every method requires the caller's email (from the JWT, set by AuthFilter) to be
 * listed in admin.emails — see OrganizationService.requireSystemAdmin.
 *
 * POST   /api/v1/admin/orgs                        create an org
 * GET    /api/v1/admin/orgs                        list all orgs
 * DELETE /api/v1/admin/orgs/{orgId}                delete an org
 * GET    /api/v1/admin/orgs/{orgId}/members        list members
 * POST   /api/v1/admin/orgs/{orgId}/members        add a member
 * DELETE /api/v1/admin/orgs/{orgId}/members/{email} remove a member
 */
@RestController
@RequestMapping("/api/v1/admin/orgs")
@RequiredArgsConstructor
@Tag(name = "Admin — Organizations", description = "Manage organizations and team members")
public class OrganizationController {

    private final OrganizationService service;

    @PostMapping
    @Operation(summary = "Create a new organization (admin only)")
    public ResponseEntity<?> create(@RequestBody Map<String, String> body, HttpServletRequest req) {
        try {
            service.requireSystemAdmin(OrgContext.from(req).email());
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        }
        String orgId = body.get("orgId");
        String name  = body.get("name");
        if (orgId == null || orgId.isBlank()) return bad("orgId is required");
        if (name  == null || name.isBlank())  return bad("name is required");
        try {
            return ResponseEntity.status(201).body(service.create(orgId.trim(), name.trim()));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    @GetMapping
    @Operation(summary = "List all organizations (admin only)")
    public ResponseEntity<?> list(HttpServletRequest req) {
        try {
            service.requireSystemAdmin(OrgContext.from(req).email());
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        }
        return ResponseEntity.ok(service.listAll());
    }

    @DeleteMapping("/{orgId}")
    @Operation(summary = "Delete an organization (admin only)")
    public ResponseEntity<?> delete(@PathVariable String orgId, HttpServletRequest req) {
        try {
            service.requireSystemAdmin(OrgContext.from(req).email());
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        }
        service.delete(orgId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{orgId}/members")
    @Operation(summary = "List members of an organization (admin only)")
    public ResponseEntity<?> listMembers(@PathVariable String orgId, HttpServletRequest req) {
        try {
            service.requireSystemAdmin(OrgContext.from(req).email());
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        }
        return ResponseEntity.ok(service.listMembers(orgId));
    }

    @PostMapping("/{orgId}/members")
    @Operation(summary = "Add a member to an organization (admin only)")
    public ResponseEntity<?> addMember(@PathVariable String orgId,
                                        @RequestBody Map<String, String> body,
                                        HttpServletRequest req) {
        try {
            service.requireSystemAdmin(OrgContext.from(req).email());
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        }
        String email    = body.get("email");
        String roleStr  = body.getOrDefault("role", "MEMBER");
        if (email == null || email.isBlank()) return bad("email is required");
        try {
            OrgMember.Role role = OrgMember.Role.valueOf(roleStr.toUpperCase());
            return ResponseEntity.status(201).body(service.addMember(orgId, email.trim().toLowerCase(), role));
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    @DeleteMapping("/{orgId}/members/{email}")
    @Operation(summary = "Remove a member from an organization (admin only)")
    public ResponseEntity<?> removeMember(@PathVariable String orgId,
                                           @PathVariable String email,
                                           HttpServletRequest req) {
        try {
            service.requireSystemAdmin(OrgContext.from(req).email());
        } catch (SecurityException e) {
            return forbidden(e.getMessage());
        }
        service.removeMember(orgId, email);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Map<String, String>> bad(String msg) {
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    private ResponseEntity<Map<String, String>> forbidden(String msg) {
        return ResponseEntity.status(403).body(Map.of("error", msg));
    }
}
