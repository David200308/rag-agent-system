package com.agentsystem.auth.org;

public interface OrgMemberRepository extends org.springframework.data.jpa.repository.JpaRepository<OrgMember, OrgMemberId> {

    boolean existsByOrgIdAndUserUuid(String orgId, String userUuid);
}
