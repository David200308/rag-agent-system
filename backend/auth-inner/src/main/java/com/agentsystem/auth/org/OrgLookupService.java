package com.agentsystem.auth.org;

import com.agentsystem.auth.user.AuthUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Read-only subset of agent-system-rest's OrganizationService needed at login time:
 * TEAM-mode membership check and the org-existence check backing the public
 * {@code GET /api/v1/auth/org/{orgId}} endpoint.
 */
@Service
@RequiredArgsConstructor
public class OrgLookupService {

    private final OrganizationRepository orgRepo;
    private final OrgMemberRepository    memberRepo;
    private final AuthUserService        userAccountService;

    public boolean isMember(String orgId, String email) {
        if (!orgRepo.existsById(orgId)) return false;
        String uuid = resolveUuid(email);
        return uuid != null && memberRepo.existsByOrgIdAndUserUuid(orgId, uuid);
    }

    public boolean orgExists(String orgId) {
        return orgRepo.existsById(orgId);
    }

    private String resolveUuid(String email) {
        if (email == null || email.isBlank()) return null;
        return userAccountService.findByEmail(email).map(com.agentsystem.auth.user.AuthUser::getUuid).orElse(null);
    }
}
