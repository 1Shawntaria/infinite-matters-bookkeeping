package com.infinitematters.bookkeeping.service;

import com.infinitematters.bookkeeping.domain.CategorizationResult;

public class ExplanationBuilder {

    public String build(CategorizationResult r) {
        return "[route=" + r.route() + "] conf="+String.format("%.2f", r.confidence().score())
                +" reason="+r.confidence().rationale()+" via="+r.explanation();
    }
}
