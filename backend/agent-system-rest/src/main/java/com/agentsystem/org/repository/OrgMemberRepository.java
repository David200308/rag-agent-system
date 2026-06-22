package com.agentsystem.org.repository;

import com.agentsystem.org.entity.OrgMember;
import com.agentsystem.org.entity.OrgMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrgMemberRepository extends JpaRepository<OrgMember, OrgMemberId> {

    List<OrgMember> findByOrgId(String orgId);

    List<OrgMember> findByEmail(String email);

    Optional<OrgMember> findByOrgIdAndEmail(String orgId, String email);

    boolean existsByOrgIdAndEmail(String orgId, String email);
}
