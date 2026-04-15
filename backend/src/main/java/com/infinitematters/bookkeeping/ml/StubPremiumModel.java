package com.infinitematters.bookkeeping.ml;

import com.infinitematters.bookkeeping.domain.CategorizationResult;
import com.infinitematters.bookkeeping.domain.Category;
import com.infinitematters.bookkeeping.domain.Confidence;
import com.infinitematters.bookkeeping.domain.Transaction;

public class StubPremiumModel implements PremiumModel {
    @Override public CategorizationResult predict(Transaction t) {
        // Here you'd call a premium LLM endpoint and parse top category + rationale.
        // Stub returns a conservative answer with an explanation.
        return new CategorizationResult(Category.OTHER,
                new Confidence(0.83, "premium: resolved ambiguity via full context"),
                "PREMIUM", "stub");
    }
}
