package com.infinitematters.bookkeeping.ml;

import com.infinitematters.bookkeeping.domain.CategorizationResult;
import com.infinitematters.bookkeeping.domain.Transaction;
import com.infinitematters.bookkeeping.rules.RulesEngine;

public class ModelRouter {
    private final RulesEngine rules = new RulesEngine();
    private final MidTierModel mid = new LocalMidTierModel();
    private final PremiumModel premium = new StubPremiumModel();

    public CategorizationResult route(Transaction t) {
        var rulesRes = rules.tryCategorize(t);
        if (rulesRes != null && rulesRes.confidence().score() >= 0.75) return rulesRes;

        var midRes = mid.predict(t);
        if (midRes.confidence().score() >= 0.70) return midRes;
        return premium.predict(t);
    }
}
