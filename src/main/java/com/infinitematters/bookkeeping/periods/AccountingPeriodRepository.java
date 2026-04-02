package com.infinitematters.bookkeeping.periods;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, UUID> {
    @Query("""
            select p from AccountingPeriod p
            where p.organization.id = :organizationId
              and :entryDate between p.periodStart and p.periodEnd
            order by p.periodStart desc
            """)
    List<AccountingPeriod> findPeriodsContaining(@Param("organizationId") UUID organizationId,
                                                 @Param("entryDate") LocalDate entryDate);

    default Optional<AccountingPeriod> findPeriodContaining(UUID organizationId, LocalDate entryDate) {
        return findPeriodsContaining(organizationId, entryDate).stream().findFirst();
    }

    List<AccountingPeriod> findByOrganizationIdOrderByPeriodStartDesc(UUID organizationId);

    Optional<AccountingPeriod> findFirstByOrganizationIdOrderByPeriodEndDesc(UUID organizationId);
}
