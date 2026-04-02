package com.infinitematters.bookkeeping.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    Optional<JournalEntry> findByTransactionId(UUID transactionId);

    List<JournalEntry> findByOrganizationIdOrderByEntryDateDescCreatedAtDesc(UUID organizationId);

    long countByOrganizationId(UUID organizationId);

    @Query("""
            select count(e) from JournalEntry e
            where e.entryType = com.infinitematters.bookkeeping.ledger.JournalEntryType.TRANSACTION
              and e.transaction is not null
              and e.transaction.financialAccount.id = :financialAccountId
              and e.entryDate between :periodStart and :periodEnd
            """)
    long countTransactionEntriesForAccountInPeriod(@Param("financialAccountId") UUID financialAccountId,
                                                   @Param("periodStart") LocalDate periodStart,
                                                   @Param("periodEnd") LocalDate periodEnd);
}
