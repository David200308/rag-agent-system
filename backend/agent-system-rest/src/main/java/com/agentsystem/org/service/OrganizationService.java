package com.agentsystem.org.service;

import com.agentsystem.org.entity.OrgMember;
import com.agentsystem.org.entity.Organization;

import java.util.List;

public interface OrganizationService {

    void requireSystemAdmin(String callerEmail);

    Organization create(String orgId, String name);

    List<Organization> listAll();

    Organization get(String orgId);

    void delete(String orgId);

    OrgMember addMember(String orgId, String email, OrgMember.Role role);

    void removeMember(String orgId, String email);

    List<OrgMember> listMembers(String orgId);

    void requireOwner(String orgId, String callerEmail);

    void transferOwner(String orgId, String currentOwnerEmail, String newOwnerEmail);

    boolean isMember(String orgId, String email);

    void requireOrgExists(String orgId);
}
