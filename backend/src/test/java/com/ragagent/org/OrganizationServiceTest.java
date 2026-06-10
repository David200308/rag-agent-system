package com.ragagent.org;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock OrganizationRepository orgRepo;
    @Mock OrgMemberRepository    memberRepo;

    @InjectMocks OrganizationService service;

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_validSlug_savesOrg() {
        when(orgRepo.existsById("skyproton")).thenReturn(false);
        when(orgRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);

        Organization result = service.create("skyproton", "SkyProton");

        verify(orgRepo).save(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo("skyproton");
        assertThat(captor.getValue().getName()).isEqualTo("SkyProton");
    }

    @Test
    void create_duplicateSlug_throwsIllegalArgument() {
        when(orgRepo.existsById("skyproton")).thenReturn(true);

        assertThatThrownBy(() -> service.create("skyproton", "Another"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void create_invalidSlug_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.create("A", "Short"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.create("has spaces", "Bad"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.create("UPPERCASE", "Bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── addMember ─────────────────────────────────────────────────────────────

    @Test
    void addMember_newEmail_savesMember() {
        when(orgRepo.existsById("skyproton")).thenReturn(true);
        when(memberRepo.existsByOrgIdAndEmail("skyproton", "alice@test.com")).thenReturn(false);
        when(memberRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        OrgMember m = service.addMember("skyproton", "alice@test.com", OrgMember.Role.MEMBER);

        assertThat(m.getOrgId()).isEqualTo("skyproton");
        assertThat(m.getEmail()).isEqualTo("alice@test.com");
        assertThat(m.getRole()).isEqualTo(OrgMember.Role.MEMBER);
    }

    @Test
    void addMember_orgNotFound_throwsIllegalArgument() {
        when(orgRepo.existsById("ghost")).thenReturn(false);

        assertThatThrownBy(() -> service.addMember("ghost", "alice@test.com", OrgMember.Role.MEMBER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void addMember_alreadyMember_throwsIllegalArgument() {
        when(orgRepo.existsById("skyproton")).thenReturn(true);
        when(memberRepo.existsByOrgIdAndEmail("skyproton", "alice@test.com")).thenReturn(true);

        assertThatThrownBy(() -> service.addMember("skyproton", "alice@test.com", OrgMember.Role.MEMBER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already a member");
    }

    // ── removeMember ──────────────────────────────────────────────────────────

    @Test
    void removeMember_delegatesToRepository() {
        service.removeMember("skyproton", "bob@test.com");

        verify(memberRepo).deleteById(new OrgMemberId("skyproton", "bob@test.com"));
    }

    // ── listMembers ───────────────────────────────────────────────────────────

    @Test
    void listMembers_returnsAllOrgMembers() {
        OrgMember m = new OrgMember("skyproton", "alice@test.com", OrgMember.Role.OWNER);
        when(memberRepo.findByOrgId("skyproton")).thenReturn(List.of(m));

        assertThat(service.listMembers("skyproton")).containsExactly(m);
    }

    // ── isMember ──────────────────────────────────────────────────────────────

    @Test
    void isMember_orgExistsAndEmailIsMember_returnsTrue() {
        when(orgRepo.existsById("skyproton")).thenReturn(true);
        when(memberRepo.existsByOrgIdAndEmail("skyproton", "alice@test.com")).thenReturn(true);

        assertThat(service.isMember("skyproton", "alice@test.com")).isTrue();
    }

    @Test
    void isMember_orgDoesNotExist_returnsFalse() {
        when(orgRepo.existsById("ghost")).thenReturn(false);

        assertThat(service.isMember("ghost", "alice@test.com")).isFalse();
        verify(memberRepo, never()).existsByOrgIdAndEmail(any(), any());
    }

    @Test
    void isMember_orgExistsButEmailNotMember_returnsFalse() {
        when(orgRepo.existsById("skyproton")).thenReturn(true);
        when(memberRepo.existsByOrgIdAndEmail("skyproton", "stranger@test.com")).thenReturn(false);

        assertThat(service.isMember("skyproton", "stranger@test.com")).isFalse();
    }

    // ── requireOwner ──────────────────────────────────────────────────────────

    @Test
    void requireOwner_ownerRole_doesNotThrow() {
        OrgMember owner = new OrgMember("skyproton", "owner@test.com", OrgMember.Role.OWNER);
        when(memberRepo.findByOrgIdAndEmail("skyproton", "owner@test.com"))
                .thenReturn(Optional.of(owner));

        // should not throw
        service.requireOwner("skyproton", "owner@test.com");
    }

    @Test
    void requireOwner_memberRole_throwsSecurityException() {
        OrgMember member = new OrgMember("skyproton", "member@test.com", OrgMember.Role.MEMBER);
        when(memberRepo.findByOrgIdAndEmail("skyproton", "member@test.com"))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.requireOwner("skyproton", "member@test.com"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("owner");
    }

    @Test
    void requireOwner_notAMember_throwsSecurityException() {
        when(memberRepo.findByOrgIdAndEmail("skyproton", "outsider@test.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireOwner("skyproton", "outsider@test.com"))
                .isInstanceOf(SecurityException.class);
    }

    // ── transferOwner ─────────────────────────────────────────────────────────

    @Test
    void transferOwner_swapsRolesAtomically() {
        OrgMember currentOwner = new OrgMember("skyproton", "owner@test.com", OrgMember.Role.OWNER);
        OrgMember newOwner     = new OrgMember("skyproton", "new@test.com",   OrgMember.Role.MEMBER);

        when(memberRepo.findByOrgIdAndEmail("skyproton", "owner@test.com"))
                .thenReturn(Optional.of(currentOwner));
        when(memberRepo.findByOrgIdAndEmail("skyproton", "new@test.com"))
                .thenReturn(Optional.of(newOwner));
        when(memberRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.transferOwner("skyproton", "owner@test.com", "new@test.com");

        assertThat(currentOwner.getRole()).isEqualTo(OrgMember.Role.MEMBER);
        assertThat(newOwner.getRole()).isEqualTo(OrgMember.Role.OWNER);
        verify(memberRepo, times(2)).save(any());
    }

    @Test
    void transferOwner_newOwnerNotMember_throwsIllegalArgument() {
        OrgMember currentOwner = new OrgMember("skyproton", "owner@test.com", OrgMember.Role.OWNER);
        when(memberRepo.findByOrgIdAndEmail("skyproton", "owner@test.com"))
                .thenReturn(Optional.of(currentOwner));
        when(memberRepo.findByOrgIdAndEmail("skyproton", "ghost@test.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.transferOwner("skyproton", "owner@test.com", "ghost@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    void transferOwner_callerNotOwner_throwsSecurityException() {
        OrgMember member = new OrgMember("skyproton", "member@test.com", OrgMember.Role.MEMBER);
        when(memberRepo.findByOrgIdAndEmail("skyproton", "member@test.com"))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() ->
                service.transferOwner("skyproton", "member@test.com", "other@test.com"))
                .isInstanceOf(SecurityException.class);
    }

    // ── listAll ───────────────────────────────────────────────────────────────

    @Test
    void listAll_returnsAllOrganizations() {
        Organization org = new Organization("skyproton", "Skyproton");
        when(orgRepo.findAll()).thenReturn(List.of(org));

        List<Organization> result = service.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrgId()).isEqualTo("skyproton");
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @Test
    void get_existingOrg_returnsOrg() {
        Organization org = new Organization("skyproton", "Skyproton");
        when(orgRepo.findById("skyproton")).thenReturn(Optional.of(org));

        Organization result = service.get("skyproton");

        assertThat(result.getOrgId()).isEqualTo("skyproton");
    }

    @Test
    void get_notFound_throwsIllegalArgument() {
        when(orgRepo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_callsDeleteById() {
        service.delete("skyproton");

        verify(orgRepo).deleteById("skyproton");
    }

    // ── requireOrgExists ──────────────────────────────────────────────────────

    @Test
    void requireOrgExists_exists_noException() {
        when(orgRepo.existsById("skyproton")).thenReturn(true);

        service.requireOrgExists("skyproton");
    }

    @Test
    void requireOrgExists_notExists_throwsIllegalArgument() {
        when(orgRepo.existsById("ghost")).thenReturn(false);

        assertThatThrownBy(() -> service.requireOrgExists("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ghost");
    }
}
