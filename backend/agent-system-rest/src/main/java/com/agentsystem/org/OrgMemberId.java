package com.agentsystem.org;

import java.io.Serializable;
import java.util.Objects;

public class OrgMemberId implements Serializable {
    private String orgId;
    private String email;

    public OrgMemberId() {}
    public OrgMemberId(String orgId, String email) { this.orgId = orgId; this.email = email; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrgMemberId id)) return false;
        return Objects.equals(orgId, id.orgId) && Objects.equals(email, id.email);
    }
    @Override public int hashCode() { return Objects.hash(orgId, email); }
}
