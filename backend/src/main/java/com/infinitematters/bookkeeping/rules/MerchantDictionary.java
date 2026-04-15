package com.infinitematters.bookkeeping.rules;

import com.infinitematters.bookkeeping.domain.Category;
import java.util.Map;

public class MerchantDictionary {
    private final Map<String, Category> dict = Map.of(
            "STARBUCKS", Category.MEALS,
            "AMZN Mktp", Category.OFFICE_SUPPLIES,
            "SHELL", Category.FUEL,
            "ADOBE", Category.SOFTWARE,
            "MICROSOFT", Category.SOFTWARE
    );
    public Category lookup(String merchant) {
        if (merchant == null) return null;
        return dict.entrySet().stream()
                .filter(e -> merchant.toUpperCase().contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
    }
}
