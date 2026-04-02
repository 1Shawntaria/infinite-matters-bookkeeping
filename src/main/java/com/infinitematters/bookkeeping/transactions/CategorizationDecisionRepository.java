package com.infinitematters.bookkeeping.transactions;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategorizationDecisionRepository extends JpaRepository<CategorizationDecision, UUID> {
    Optional<CategorizationDecision> findTopByTransactionIdOrderByCreatedAtDesc(UUID transactionId);

    List<CategorizationDecision> findByTransactionOrganizationId(UUID organizationId);

    @Query("""
            select d
            from CategorizationDecision d
            where d.transaction.organization.id = :organizationId
              and upper(d.transaction.merchant) = upper(:merchant)
              and d.status in :statuses
            order by d.updatedAt desc
            """)
    List<CategorizationDecision> findMemoryCandidates(@Param("organizationId") UUID organizationId,
                                                      @Param("merchant") String merchant,
                                                      @Param("statuses") List<DecisionStatus> statuses);

    default Optional<CategorizationDecision> findTopMemoryCandidate(UUID organizationId, String merchant,
                                                                    List<DecisionStatus> statuses) {
        return findMemoryCandidates(organizationId, merchant, statuses).stream().findFirst();
    }
}
