package com.infinitematters.bookkeeping.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationCloseTemplateItemRepository extends JpaRepository<OrganizationCloseTemplateItem, UUID> {
    List<OrganizationCloseTemplateItem> findByOrganizationIdOrderBySortOrderAscCreatedAtAsc(UUID organizationId);

    Optional<OrganizationCloseTemplateItem> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("select max(item.sortOrder) from OrganizationCloseTemplateItem item where item.organization.id = :organizationId")
    Integer findMaxSortOrderByOrganizationId(UUID organizationId);
}
