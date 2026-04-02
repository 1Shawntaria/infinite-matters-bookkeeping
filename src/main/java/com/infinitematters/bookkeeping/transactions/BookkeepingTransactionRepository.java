package com.infinitematters.bookkeeping.transactions;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.infinitematters.bookkeeping.accounts.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookkeepingTransactionRepository extends JpaRepository<BookkeepingTransaction, UUID> {
    Optional<BookkeepingTransaction> findByOrganizationIdAndSourceFingerprint(UUID organizationId, String sourceFingerprint);

    List<BookkeepingTransaction> findByOrganizationIdOrderByTransactionDateDescCreatedAtDesc(UUID organizationId);

    List<BookkeepingTransaction> findByOrganizationIdAndStatusOrderByTransactionDateDescCreatedAtDesc(UUID organizationId,
                                                                                                      TransactionStatus status);

    Optional<BookkeepingTransaction> findFirstByOrganizationIdOrderByTransactionDateDescCreatedAtDesc(UUID organizationId);

    @Query("""
            select coalesce(sum(t.amount), 0)
            from BookkeepingTransaction t
            where t.organization.id = :organizationId
              and t.status = :status
              and t.financialAccount.accountType in :accountTypes
            """)
    BigDecimal sumAmountsByOrganizationAndStatusAndAccountTypes(@Param("organizationId") UUID organizationId,
                                                                @Param("status") TransactionStatus status,
                                                                @Param("accountTypes") List<AccountType> accountTypes);

    @Query("""
            select coalesce(sum(t.amount), 0)
            from BookkeepingTransaction t
            where t.financialAccount.id = :financialAccountId
              and t.transactionDate between :periodStart and :periodEnd
              and t.status = :status
            """)
    BigDecimal sumPostedAmountsForAccountInPeriod(@Param("financialAccountId") UUID financialAccountId,
                                                  @Param("periodStart") LocalDate periodStart,
                                                  @Param("periodEnd") LocalDate periodEnd,
                                                  @Param("status") TransactionStatus status);
}
