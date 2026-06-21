package com.ragagent.org;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationControllerTest {

    @Mock OrganizationService service;
    @Mock HttpServletRequest  request;

    @InjectMocks OrganizationController controller;

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stubAdmin(String email) {
        when(request.getAttribute("authenticatedEmail")).thenReturn(email);
    }

    private void stubNotAdmin(String email) {
        when(request.getAttribute("authenticatedEmail")).thenReturn(email);
        doThrow(new SecurityException("Admin access required."))
                .when(service).requireSystemAdmin(email);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_success_returns201() {
        stubAdmin("admin@test.com");
        Organization org = new Organization("acme", "Acme Corp");
        when(service.create("acme", "Acme Corp")).thenReturn(org);

        ResponseEntity<?> resp = controller.create(Map.of("orgId", "acme", "name", "Acme Corp"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody()).isEqualTo(org);
        verify(service).requireSystemAdmin("admin@test.com");
    }

    @Test
    void create_callerNotAdmin_returns403() {
        stubNotAdmin("stranger@test.com");

        ResponseEntity<?> resp = controller.create(Map.of("orgId", "acme", "name", "Acme Corp"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        verify(service, never()).create(any(), any());
    }

    @Test
    void create_missingOrgId_returns400() {
        stubAdmin("admin@test.com");
        ResponseEntity<?> resp = controller.create(Map.of("name", "Acme Corp"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) resp.getBody();
        assertThat(body).containsEntry("error", "orgId is required");
        verify(service, never()).create(any(), any());
    }

    @Test
    void create_blankOrgId_returns400() {
        stubAdmin("admin@test.com");
        ResponseEntity<?> resp = controller.create(Map.of("orgId", "  ", "name", "Acme Corp"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        verify(service, never()).create(any(), any());
    }

    @Test
    void create_missingName_returns400() {
        stubAdmin("admin@test.com");
        ResponseEntity<?> resp = controller.create(Map.of("orgId", "acme"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) resp.getBody();
        assertThat(body).containsEntry("error", "name is required");
        verify(service, never()).create(any(), any());
    }

    @Test
    void create_duplicate_returns400() {
        stubAdmin("admin@test.com");
        when(service.create("acme", "Acme Corp"))
                .thenThrow(new IllegalArgumentException("Organization already exists: acme"));

        ResponseEntity<?> resp = controller.create(Map.of("orgId", "acme", "name", "Acme Corp"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) resp.getBody();
        assertThat(body.get("error")).contains("already exists");
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_returns200WithOrganizations() {
        stubAdmin("admin@test.com");
        Organization org = new Organization("acme", "Acme Corp");
        when(service.listAll()).thenReturn(List.of(org));

        ResponseEntity<?> resp = controller.list(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEqualTo(List.of(org));
    }

    @Test
    void list_empty_returns200EmptyList() {
        stubAdmin("admin@test.com");
        when(service.listAll()).thenReturn(List.of());

        ResponseEntity<?> resp = controller.list(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEqualTo(List.of());
    }

    @Test
    void list_callerNotAdmin_returns403() {
        stubNotAdmin("stranger@test.com");

        ResponseEntity<?> resp = controller.list(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        verify(service, never()).listAll();
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_returns204() {
        stubAdmin("admin@test.com");
        doNothing().when(service).delete("acme");

        ResponseEntity<?> resp = controller.delete("acme", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
        verify(service).delete("acme");
    }

    @Test
    void delete_callerNotAdmin_returns403() {
        stubNotAdmin("stranger@test.com");

        ResponseEntity<?> resp = controller.delete("acme", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        verify(service, never()).delete(any());
    }

    // ── listMembers ───────────────────────────────────────────────────────────

    @Test
    void listMembers_returns200WithMembers() {
        stubAdmin("admin@test.com");
        OrgMember member = new OrgMember("acme", "owner@test.com", OrgMember.Role.OWNER);
        when(service.listMembers("acme")).thenReturn(List.of(member));

        ResponseEntity<?> resp = controller.listMembers("acme", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEqualTo(List.of(member));
    }

    @Test
    void listMembers_emptyOrg_returns200EmptyList() {
        stubAdmin("admin@test.com");
        when(service.listMembers("acme")).thenReturn(List.of());

        ResponseEntity<?> resp = controller.listMembers("acme", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEqualTo(List.of());
    }

    @Test
    void listMembers_callerNotAdmin_returns403() {
        stubNotAdmin("stranger@test.com");

        ResponseEntity<?> resp = controller.listMembers("acme", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        verify(service, never()).listMembers(any());
    }

    // ── addMember ─────────────────────────────────────────────────────────────

    @Test
    void addMember_success_returns201() {
        stubAdmin("admin@test.com");
        OrgMember member = new OrgMember("acme", "user@test.com", OrgMember.Role.MEMBER);
        when(service.addMember("acme", "user@test.com", OrgMember.Role.MEMBER)).thenReturn(member);

        ResponseEntity<?> resp = controller.addMember("acme",
                Map.of("email", "user@test.com", "role", "MEMBER"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody()).isEqualTo(member);
    }

    @Test
    void addMember_callerNotAdmin_returns403() {
        stubNotAdmin("stranger@test.com");

        ResponseEntity<?> resp = controller.addMember("acme",
                Map.of("email", "user@test.com", "role", "MEMBER"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        verify(service, never()).addMember(any(), any(), any());
    }

    @Test
    void addMember_missingEmail_returns400() {
        stubAdmin("admin@test.com");
        ResponseEntity<?> resp = controller.addMember("acme", Map.of("role", "MEMBER"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) resp.getBody();
        assertThat(body).containsEntry("error", "email is required");
        verify(service, never()).addMember(any(), any(), any());
    }

    @Test
    void addMember_blankEmail_returns400() {
        stubAdmin("admin@test.com");
        ResponseEntity<?> resp = controller.addMember("acme", Map.of("email", "   ", "role", "MEMBER"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        verify(service, never()).addMember(any(), any(), any());
    }

    @Test
    void addMember_badRole_returns400() {
        stubAdmin("admin@test.com");
        // OrgMember.Role.valueOf("SUPERADMIN") throws IllegalArgumentException
        ResponseEntity<?> resp = controller.addMember("acme",
                Map.of("email", "user@test.com", "role", "SUPERADMIN"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void addMember_defaultRoleMember_usedWhenRoleOmitted() {
        stubAdmin("admin@test.com");
        OrgMember member = new OrgMember("acme", "user@test.com", OrgMember.Role.MEMBER);
        when(service.addMember("acme", "user@test.com", OrgMember.Role.MEMBER)).thenReturn(member);

        ResponseEntity<?> resp = controller.addMember("acme", Map.of("email", "user@test.com"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
    }

    // ── removeMember ──────────────────────────────────────────────────────────

    @Test
    void removeMember_returns204() {
        stubAdmin("admin@test.com");
        doNothing().when(service).removeMember("acme", "user@test.com");

        ResponseEntity<?> resp = controller.removeMember("acme", "user@test.com", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
        verify(service).removeMember("acme", "user@test.com");
    }

    @Test
    void removeMember_callerNotAdmin_returns403() {
        stubNotAdmin("stranger@test.com");

        ResponseEntity<?> resp = controller.removeMember("acme", "user@test.com", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        verify(service, never()).removeMember(any(), any());
    }
}
