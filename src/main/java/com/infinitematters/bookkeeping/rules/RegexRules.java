package com.infinitematters.bookkeeping.rules;

import com.infinitematters.bookkeeping.domain.Category;
import java.util.regex.Pattern;

public class RegexRules {
    private static final Pattern MEALS = Pattern.compile("DINER|CAFE|FOOD|RESTAURANT", Pattern.CASE_INSENSITIVE);
    private static final Pattern SOFTWARE = Pattern.compile("SAAS|SUBSCRIPTION|CLOUD|LICENSE", Pattern.CASE_INSENSITIVE);

    public Category byMemo(String memo) {
        if (memo == null) return null;
        return MEALS.matcher(memo).find() ? Category.MEALS :
                SOFTWARE.matcher(memo).find() ? Category.SOFTWARE : null;
    }

}
