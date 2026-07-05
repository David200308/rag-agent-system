package com.agentsystem.org.repository;

import com.agentsystem.org.entity.OrgMember;
import com.agentsystem.org.entity.OrgMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrgMemberRepository extends JpaRepository<OrgMember, OrgMemberId> {

    List<OrgMember> findByOrgId(String orgId);

    List<OrgMember> findByUserUuid(String userUuid);

    Optional<OrgMember> findByOrgIdAndUserUuid(String orgId, String userUuid);

    boolean existsByOrgIdAndUserUuid(String orgId, String userUuid);
}
