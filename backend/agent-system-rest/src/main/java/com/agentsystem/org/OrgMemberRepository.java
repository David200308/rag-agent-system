package com.agentsystem.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrgMemberRepository extends JpaRepository<OrgMember, OrgMemberId> {

    List<OrgMember> findByOrgId(String orgId);

    List<OrgMember> findByEmail(String email);

    Optional<OrgMember> findByOrgIdAndEmail(String orgId, String email);

    boolean existsByOrgIdAndEmail(String orgId, String email);
}
