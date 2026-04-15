package com.infinitematters.bookkeeping.users;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {
    Optional<OrganizationMembership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    List<OrganizationMembership> findByUserId(UUID userId);

    List<OrganizationMembership> findByOrganizationIdAndRoleIn(UUID organizationId, List<UserRole> roles);
}
