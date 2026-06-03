package com.ragagent.org;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Carries the authenticated identity extracted from the JWT by AuthFilter.
 * Controllers build one per request via {@link #from(HttpServletRequest)}.
 */
public record OrgContext(String email, String mode, String orgId) {

    public boolean isTeam()     { return "TEAM".equals(mode); }
    public boolean isPersonal() { return !isTeam(); }

    public static OrgContext from(HttpServletRequest req) {
        String email = (String) req.getAttribute("authenticatedEmail");
        String mode  = (String) req.getAttribute("authenticatedMode");
        String orgId = (String) req.getAttribute("authenticatedOrgId");
        return new OrgContext(email, mode != null ? mode : "PERSONAL", orgId);
    }
}
