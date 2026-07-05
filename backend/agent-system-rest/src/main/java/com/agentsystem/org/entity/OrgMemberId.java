package com.agentsystem.org.entity;

import java.io.Serializable;
import java.util.Objects;

public class OrgMemberId implements Serializable {
    private String orgId;
    private String userUuid;

    public OrgMemberId() {}
    public OrgMemberId(String orgId, String userUuid) { this.orgId = orgId; this.userUuid = userUuid; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrgMemberId id)) return false;
        return Objects.equals(orgId, id.orgId) && Objects.equals(userUuid, id.userUuid);
    }
    @Override public int hashCode() { return Objects.hash(orgId, userUuid); }
}
