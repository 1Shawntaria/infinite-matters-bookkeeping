package com.infinitematters.bookkeeping.ml;

import com.infinitematters.bookkeeping.domain.CategorizationResult;
import com.infinitematters.bookkeeping.domain.Category;
import com.infinitematters.bookkeeping.domain.Confidence;
import com.infinitematters.bookkeeping.domain.Transaction;

public class LocalMidTierModel implements MidTierModel {
    @Override public CategorizationResult predict(Transaction t) {
        // Placeholder heuristic: use memo * amount patterns.
        // In real life, plug in a local model or lightweight classifier.
        if (t.memo != null && t.memo.toLowerCase().contains("subscription")) {
            return new CategorizationResult(Category.SOFTWARE,
                    new Confidence(0.68, "keyword: subscription"), "MID", "heuristic");
        }
        return new CategorizationResult(Category.OTHER,
                new Confidence(0.55, "fallback mid-tier"), "MID", "fallback");
    }
}
