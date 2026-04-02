package com.infinitematters.bookkeeping.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationSessionRepository extends JpaRepository<ReconciliationSession, UUID> {
    Optional<ReconciliationSession> findByFinancialAccountIdAndPeriodStartAndPeriodEnd(UUID financialAccountId,
                                                                                        LocalDate periodStart,
                                                                                        LocalDate periodEnd);

    List<ReconciliationSession> findByOrganizationIdAndPeriodStartAndPeriodEnd(UUID organizationId,
                                                                                LocalDate periodStart,
                                                                                LocalDate periodEnd);

    List<ReconciliationSession> findByOrganizationIdOrderByPeriodStartDescCreatedAtDesc(UUID organizationId);
}
