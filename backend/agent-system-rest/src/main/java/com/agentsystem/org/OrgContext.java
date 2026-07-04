package com.agentsystem.org;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Carries the authenticated identity extracted from the JWT by AuthFilter.
 * Controllers build one per request via {@link #from(HttpServletRequest)}.
 *
 * {@code userUuid} is the JWT's subject (the identity going forward); {@code email} is
 * resolved from it by AuthFilter and kept around because most tables still key rows by
 * plaintext email (Phase 2 will migrate them to user_uuid).
 */
public record OrgContext(String userUuid, String email, String mode, String orgId) {

    /** Back-compat for the many call sites built directly from an email string (no uuid known). */
    public OrgContext(String email, String mode, String orgId) {
        this(null, email, mode, orgId);
    }

    public boolean isTeam()     { return "TEAM".equals(mode); }
    public boolean isPersonal() { return !isTeam(); }

    public static OrgContext from(HttpServletRequest req) {
        String userUuid = (String) req.getAttribute("authenticatedUserUuid");
        String email    = (String) req.getAttribute("authenticatedEmail");
        String mode     = (String) req.getAttribute("authenticatedMode");
        String orgId    = (String) req.getAttribute("authenticatedOrgId");
        return new OrgContext(userUuid, email, mode != null ? mode : "PERSONAL", orgId);
    }
}
