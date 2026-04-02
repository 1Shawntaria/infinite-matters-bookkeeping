package com.infinitematters.bookkeeping.close;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountCloseStateRepository extends JpaRepository<AccountCloseState, UUID> {
    Optional<AccountCloseState> findByFinancialAccountIdAndPeriodStartAndPeriodEnd(UUID financialAccountId,
                                                                                   LocalDate periodStart,
                                                                                   LocalDate periodEnd);

    List<AccountCloseState> findByOrganizationIdAndPeriodStartAndPeriodEnd(UUID organizationId,
                                                                           LocalDate periodStart,
                                                                           LocalDate periodEnd);
}
