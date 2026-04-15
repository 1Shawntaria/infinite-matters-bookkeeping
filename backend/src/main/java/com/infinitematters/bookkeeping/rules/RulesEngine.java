package com.infinitematters.bookkeeping.rules;

import com.infinitematters.bookkeeping.domain.CategorizationResult;
import com.infinitematters.bookkeeping.domain.Category;
import com.infinitematters.bookkeeping.domain.Confidence;
import com.infinitematters.bookkeeping.domain.Transaction;

public class RulesEngine {
    private final MerchantDictionary dict = new MerchantDictionary();
    private final RegexRules regex = new RegexRules();

    public CategorizationResult tryCategorize(Transaction t) {
        Category byMerchant = dict.lookup(t.merchant);

        if(byMerchant != null) {
            return new CategorizationResult(byMerchant,
                    new Confidence(0.92, "Known merchant -> "+ byMerchant),
                    "RULES", "merchant_dict");
        }
        Category byMemo = regex.byMemo(t.memo);

        if (byMemo != null) {
            return new CategorizationResult(byMemo,
                    new Confidence(0.80, "Memo regex -> "+ byMemo),
                    "RULES", "regex");
        }
        //soft signal from MCC if present
        if (t.mcc != null && t.mcc.startsWith("581")) {
            return new CategorizationResult(Category.MEALS,
                    new Confidence(0.72, "MCC 581x -> meals"), "RULES", "mcc");
        }
        return null;
    }
}
