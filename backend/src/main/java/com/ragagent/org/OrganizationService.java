package com.ragagent.org;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository orgRepo;
    private final OrgMemberRepository    memberRepo;

    // ── Org CRUD ──────────────────────────────────────────────────────────────

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

    @Transactional(readOnly = true)
    public List<Organization> listAll() {
        return orgRepo.findAll();
    }

    @Transactional(readOnly = true)
    public Organization get(String orgId) {
        return orgRepo.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
    }

    @Transactional
    public void delete(String orgId) {
        orgRepo.deleteById(orgId);
        log.info("[OrganizationService] Deleted org '{}'", orgId);
    }

    // ── Member management ──────────────────────────────────────────────────────

    @Transactional
    public OrgMember addMember(String orgId, String email, OrgMember.Role role) {
        if (!orgRepo.existsById(orgId)) {
            throw new IllegalArgumentException("Organization not found: " + orgId);
        }
        if (memberRepo.existsByOrgIdAndEmail(orgId, email)) {
            throw new IllegalArgumentException(email + " is already a member of " + orgId);
        }
        OrgMember m = memberRepo.save(new OrgMember(orgId, email, role));
        log.info("[OrganizationService] Added {} as {} to org '{}'", email, role, orgId);
        return m;
    }

    @Transactional
    public void removeMember(String orgId, String email) {
        memberRepo.deleteById(new OrgMemberId(orgId, email));
        log.info("[OrganizationService] Removed {} from org '{}'", email, orgId);
    }

    @Transactional(readOnly = true)
    public List<OrgMember> listMembers(String orgId) {
        return memberRepo.findByOrgId(orgId);
    }

    // ── Self-service team management (called from in-app, not admin) ─────────

    /**
     * Requires the caller to be OWNER of the org; throws SecurityException if not.
     */
    public void requireOwner(String orgId, String callerEmail) {
        OrgMember m = memberRepo.findByOrgIdAndEmail(orgId, callerEmail)
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
    @Transactional
    public void transferOwner(String orgId, String currentOwnerEmail, String newOwnerEmail) {
        requireOwner(orgId, currentOwnerEmail);

        OrgMember newOwner = memberRepo.findByOrgIdAndEmail(orgId, newOwnerEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        newOwnerEmail + " is not a member of " + orgId));

        OrgMember currentOwner = memberRepo.findByOrgIdAndEmail(orgId, currentOwnerEmail)
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
    public boolean isMember(String orgId, String email) {
        return orgRepo.existsById(orgId) && memberRepo.existsByOrgIdAndEmail(orgId, email);
    }

    /** Validates that the org exists; throws if not. */
    public void requireOrgExists(String orgId) {
        if (!orgRepo.existsById(orgId)) {
            throw new IllegalArgumentException("Organization not found: " + orgId);
        }
    }
}
