package com.infinitematters.bookkeeping.ledger;

import com.infinitematters.bookkeeping.domain.Category;

public record LedgerAccountMapping(String code, String name) {
    public static LedgerAccountMapping forCategory(Category category) {
        return switch (category) {
            case MEALS -> new LedgerAccountMapping("6100", "Meals and Entertainment");
            case OFFICE_SUPPLIES -> new LedgerAccountMapping("6110", "Office Supplies");
            case SOFTWARE -> new LedgerAccountMapping("6120", "Software and Subscriptions");
            case FUEL -> new LedgerAccountMapping("6130", "Fuel");
            case TRAVEL -> new LedgerAccountMapping("6140", "Travel");
            case RENT -> new LedgerAccountMapping("6150", "Rent");
            case UTILITIES -> new LedgerAccountMapping("6160", "Utilities");
            case INCOME -> new LedgerAccountMapping("4000", "Operating Income");
            case TRANSFER -> new LedgerAccountMapping("2100", "Transfers Clearing");
            case OTHER -> new LedgerAccountMapping("6999", "Other Business Expenses");
        };
    }
}
