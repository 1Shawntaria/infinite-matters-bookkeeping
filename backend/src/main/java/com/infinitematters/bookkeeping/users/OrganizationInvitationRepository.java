package com.infinitematters.bookkeeping.users;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationInvitationRepository extends JpaRepository<OrganizationInvitation, UUID> {
    List<OrganizationInvitation> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<OrganizationInvitation> findByTokenHash(String tokenHash);

    boolean existsByOrganizationIdAndEmailIgnoreCaseAndStatus(UUID organizationId,
                                                              String email,
                                                              OrganizationInvitationStatus status);
}
