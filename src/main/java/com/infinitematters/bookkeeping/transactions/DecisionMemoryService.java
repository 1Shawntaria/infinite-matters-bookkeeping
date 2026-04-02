package com.infinitematters.bookkeeping.transactions;

import com.infinitematters.bookkeeping.domain.CategorizationResult;
import com.infinitematters.bookkeeping.domain.Category;
import com.infinitematters.bookkeeping.domain.Confidence;
import com.infinitematters.bookkeeping.domain.Transaction;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DecisionMemoryService {
    private static final List<DecisionStatus> LEARNED_STATUSES = List.of(
            DecisionStatus.AUTO_ACCEPTED,
            DecisionStatus.USER_CONFIRMED);

    private final CategorizationDecisionRepository decisionRepository;

    public DecisionMemoryService(CategorizationDecisionRepository decisionRepository) {
        this.decisionRepository = decisionRepository;
    }

    public Optional<CategorizationResult> recall(UUID organizationId, Transaction transaction) {
        if (transaction.merchant == null || transaction.merchant.isBlank()) {
            return Optional.empty();
        }

        return decisionRepository.findTopMemoryCandidate(
                        organizationId,
                        transaction.merchant,
                        LEARNED_STATUSES)
                .flatMap(decision -> resolveCategory(decision)
                        .map(category -> new CategorizationResult(
                                category,
                                new Confidence(0.97, "Learned from prior accepted categorization for merchant"),
                                "MEMORY",
                                "merchant-history")));
    }

    private Optional<Category> resolveCategory(CategorizationDecision decision) {
        return Optional.ofNullable(decision.getFinalCategory() != null
                ? decision.getFinalCategory()
                : decision.getProposedCategory());
    }
}
