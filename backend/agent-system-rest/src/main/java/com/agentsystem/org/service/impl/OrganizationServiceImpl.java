package com.agentsystem.org.service.impl;

import com.agentsystem.org.service.OrganizationService;

import com.agentsystem.config.AdminProperties;
import com.agentsystem.org.entity.OrgMember;
import com.agentsystem.org.entity.OrgMemberId;
import com.agentsystem.org.entity.Organization;
import com.agentsystem.org.repository.OrgMemberRepository;
import com.agentsystem.org.repository.OrganizationRepository;
import com.agentsystem.user.entity.User;
import com.agentsystem.user.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository orgRepo;
    private final OrgMemberRepository    memberRepo;
    private final AdminProperties        adminProperties;
    private final UserAccountService     userAccountService;

    /**
     * Requires the caller to be a configured system admin (admin.emails); throws
     * SecurityException if not. Guards the /api/v1/admin/orgs/** endpoints, which
     * operate across all organizations and are not scoped by org membership.
     */
    @Override
    public void requireSystemAdmin(String callerEmail) {
        if (!adminProperties.isAdmin(callerEmail)) {
            throw new SecurityException("Admin access required.");
        }
    }

    // ── Org CRUD ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Organization create(String orgId, String name) {
        if (!orgId.matches("[a-z0-9][a-z0-9\\-]{1,98}[a-z0-9]")) {
            throw new IllegalArgumentException(
                    "org_id must be 3–100 lowercase alphanumeric characters or hyphens.");
        }
        if (orgRepo.existsById(orgId)) {
            throw new IllegalArgumentException("Organization already exists: " + orgId);
        }
        Organization org = orgRepo.save(new Organization(orgId, name));
        log.info("[OrganizationService] Created org '{}' ({})", orgId, name);
        return org;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Organization> listAll() {
        return orgRepo.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Organization get(String orgId) {
        return orgRepo.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
    }

    @Override
    @Transactional
    public void delete(String orgId) {
        orgRepo.deleteById(orgId);
        log.info("[OrganizationService] Deleted org '{}'", orgId);
    }

    // ── Member management ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public OrgMember addMember(String orgId, String email, OrgMember.Role role) {
        if (!orgRepo.existsById(orgId)) {
            throw new IllegalArgumentException("Organization not found: " + orgId);
        }
        String uuid = resolveUuid(email);
        if (uuid == null) {
            throw new IllegalArgumentException(email + " is not a registered user.");
        }
        if (memberRepo.existsByOrgIdAndUserUuid(orgId, uuid)) {
            throw new IllegalArgumentException(email + " is already a member of " + orgId);
        }
        OrgMember m = memberRepo.save(new OrgMember(orgId, uuid, email, role));
        log.info("[OrganizationService] Added {} as {} to org '{}'", email, role, orgId);
        return m;
    }

    @Override
    @Transactional
    public void removeMember(String orgId, String email) {
        String uuid = resolveUuid(email);
        if (uuid == null) {
            throw new IllegalArgumentException(email + " is not a registered user.");
        }
        memberRepo.deleteById(new OrgMemberId(orgId, uuid));
        log.info("[OrganizationService] Removed {} from org '{}'", email, orgId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrgMember> listMembers(String orgId) {
        return memberRepo.findByOrgId(orgId);
    }

    // ── Self-service team management (called from in-app, not admin) ─────────

    /**
     * Requires the caller to be OWNER of the org; throws SecurityException if not.
     */
    @Override
    public void requireOwner(String orgId, String callerEmail) {
        String uuid = resolveUuid(callerEmail);
        OrgMember m = (uuid != null ? memberRepo.findByOrgIdAndUserUuid(orgId, uuid) : Optional.<OrgMember>empty())
                .orElseThrow(() -> new SecurityException("You are not a member of " + orgId));
        if (m.getRole() != OrgMember.Role.OWNER) {
            throw new SecurityException("Only the organization owner can perform this action.");
        }
    }

    /**
     * Transfer ownership: current owner → newOwnerEmail.
     * The previous owner becomes a MEMBER.
     * Atomic within a single transaction.
     */
    @Override
    @Transactional
    public void transferOwner(String orgId, String currentOwnerEmail, String newOwnerEmail) {
        requireOwner(orgId, currentOwnerEmail);

        String newUuid = resolveUuid(newOwnerEmail);
        OrgMember newOwner = (newUuid != null ? memberRepo.findByOrgIdAndUserUuid(orgId, newUuid) : Optional.<OrgMember>empty())
                .orElseThrow(() -> new IllegalArgumentException(
                        newOwnerEmail + " is not a member of " + orgId));

        OrgMember currentOwner = memberRepo.findByOrgIdAndUserUuid(orgId, resolveUuid(currentOwnerEmail))
                .orElseThrow();

        currentOwner.setRole(OrgMember.Role.MEMBER);
        newOwner.setRole(OrgMember.Role.OWNER);

        memberRepo.save(currentOwner);
        memberRepo.save(newOwner);

        log.info("[OrganizationService] Ownership of '{}' transferred from {} to {}",
                orgId, currentOwnerEmail, newOwnerEmail);
    }

    // ── Validation helpers used by auth ───────────────────────────────────────

    /** Returns true if the org exists and the email is a member. */
    @Override
    public boolean isMember(String orgId, String email) {
        if (!orgRepo.existsById(orgId)) return false;
        String uuid = resolveUuid(email);
        return uuid != null && memberRepo.existsByOrgIdAndUserUuid(orgId, uuid);
    }

    /** Validates that the org exists; throws if not. */
    @Override
    public void requireOrgExists(String orgId) {
        if (!orgRepo.existsById(orgId)) {
            throw new IllegalArgumentException("Organization not found: " + orgId);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /** Resolves an email to its user_uuid, or null if no such email is a registered user. */
    private String resolveUuid(String email) {
        if (email == null || email.isBlank()) return null;
        return userAccountService.findByEmail(email).map(User::getUuid).orElse(null);
    }
}
