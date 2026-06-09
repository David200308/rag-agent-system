package com.ragagent.org;

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

    @InjectMocks OrganizationController controller;

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_success_returns201() {
        Organization org = new Organization("acme", "Acme Corp");
        when(service.create("acme", "Acme Corp")).thenReturn(org);

        ResponseEntity<?> resp = controller.create(Map.of("orgId", "acme", "name", "Acme Corp"));

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody()).isEqualTo(org);
    }

    @Test
    void create_missingOrgId_returns400() {
        ResponseEntity<?> resp = controller.create(Map.of("name", "Acme Corp"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) resp.getBody();
        assertThat(body).containsEntry("error", "orgId is required");
        verifyNoInteractions(service);
    }

    @Test
    void create_blankOrgId_returns400() {
        ResponseEntity<?> resp = controller.create(Map.of("orgId", "  ", "name", "Acme Corp"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(service);
    }

    @Test
    void create_missingName_returns400() {
        ResponseEntity<?> resp = controller.create(Map.of("orgId", "acme"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) resp.getBody();
        assertThat(body).containsEntry("error", "name is required");
        verifyNoInteractions(service);
    }

    @Test
    void create_duplicate_returns400() {
        when(service.create("acme", "Acme Corp"))
                .thenThrow(new IllegalArgumentException("Organization already exists: acme"));

        ResponseEntity<?> resp = controller.create(Map.of("orgId", "acme", "name", "Acme Corp"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) resp.getBody();
        assertThat(body.get("error")).contains("already exists");
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_returns200WithOrganizations() {
        Organization org = new Organization("acme", "Acme Corp");
        when(service.listAll()).thenReturn(List.of(org));

        ResponseEntity<List<Organization>> resp = controller.list();

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsExactly(org);
    }

    @Test
    void list_empty_returns200EmptyList() {
        when(service.listAll()).thenReturn(List.of());

        ResponseEntity<List<Organization>> resp = controller.list();

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEmpty();
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_returns204() {
        doNothing().when(service).delete("acme");

        ResponseEntity<Void> resp = controller.delete("acme");

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
        verify(service).delete("acme");
    }

    // ── listMembers ───────────────────────────────────────────────────────────

    @Test
    void listMembers_returns200WithMembers() {
        OrgMember member = new OrgMember("acme", "owner@test.com", OrgMember.Role.OWNER);
        when(service.listMembers("acme")).thenReturn(List.of(member));

        ResponseEntity<List<OrgMember>> resp = controller.listMembers("acme");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsExactly(member);
    }

    @Test
    void listMembers_emptyOrg_returns200EmptyList() {
        when(service.listMembers("acme")).thenReturn(List.of());

        ResponseEntity<List<OrgMember>> resp = controller.listMembers("acme");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEmpty();
    }

    // ── addMember ─────────────────────────────────────────────────────────────

    @Test
    void addMember_success_returns201() {
        OrgMember member = new OrgMember("acme", "user@test.com", OrgMember.Role.MEMBER);
        when(service.addMember("acme", "user@test.com", OrgMember.Role.MEMBER)).thenReturn(member);

        ResponseEntity<?> resp = controller.addMember("acme",
                Map.of("email", "user@test.com", "role", "MEMBER"));

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody()).isEqualTo(member);
    }

    @Test
    void addMember_missingEmail_returns400() {
        ResponseEntity<?> resp = controller.addMember("acme", Map.of("role", "MEMBER"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) resp.getBody();
        assertThat(body).containsEntry("error", "email is required");
        verifyNoInteractions(service);
    }

    @Test
    void addMember_blankEmail_returns400() {
        ResponseEntity<?> resp = controller.addMember("acme", Map.of("email", "   ", "role", "MEMBER"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(service);
    }

    @Test
    void addMember_badRole_returns400() {
        // OrgMember.Role.valueOf("SUPERADMIN") throws IllegalArgumentException
        ResponseEntity<?> resp = controller.addMember("acme",
                Map.of("email", "user@test.com", "role", "SUPERADMIN"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void addMember_defaultRoleMember_usedWhenRoleOmitted() {
        OrgMember member = new OrgMember("acme", "user@test.com", OrgMember.Role.MEMBER);
        when(service.addMember("acme", "user@test.com", OrgMember.Role.MEMBER)).thenReturn(member);

        ResponseEntity<?> resp = controller.addMember("acme", Map.of("email", "user@test.com"));

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
    }

    // ── removeMember ──────────────────────────────────────────────────────────

    @Test
    void removeMember_returns204() {
        doNothing().when(service).removeMember("acme", "user@test.com");

        ResponseEntity<Void> resp = controller.removeMember("acme", "user@test.com");

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
        verify(service).removeMember("acme", "user@test.com");
    }
}
