package com.infinitematters.bookkeeping.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PeriodClosePlaybookItemRepository extends JpaRepository<PeriodClosePlaybookItem, UUID> {
    List<PeriodClosePlaybookItem> findByOrganizationIdAndMonthOrderByCreatedAtAsc(UUID organizationId, String month);

    Optional<PeriodClosePlaybookItem> findByOrganizationIdAndTemplateItemIdAndMonth(
            UUID organizationId,
            UUID templateItemId,
            String month);
}
