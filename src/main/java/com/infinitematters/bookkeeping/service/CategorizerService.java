package com.infinitematters.bookkeeping.service;

import com.infinitematters.bookkeeping.domain.CategorizationResult;
import com.infinitematters.bookkeeping.domain.Transaction;
import com.infinitematters.bookkeeping.ml.ModelRouter;
import org.springframework.stereotype.Service;

@Service
public class CategorizerService {
    private final ModelRouter router = new ModelRouter();
    private final ExplanationBuilder explain = new ExplanationBuilder();

    public CategorizationResult categorize(Transaction t) {
        CategorizationResult r = router.route(t);
        return new CategorizationResult(r.category(),
                r.confidence(),
                r.route(),
                explain.build(r));
    }
}
