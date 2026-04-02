package com.infinitematters.bookkeeping.ingest;

import com.infinitematters.bookkeeping.domain.Transaction;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class Normalizer {
    public Transaction normalize(Transaction transaction) {
        if (transaction.merchant != null) {
            transaction.merchant = transaction.merchant.trim();
            if (!transaction.merchant.isBlank()) {
                transaction.merchant = transaction.merchant.toUpperCase(Locale.US);
            }
        }
        if (transaction.memo != null) {
            transaction.memo = transaction.memo.trim();
            if (transaction.memo.isBlank()) {
                transaction.memo = null;
            }
        }
        if (transaction.mcc != null) {
            transaction.mcc = transaction.mcc.trim();
            if (transaction.mcc.isBlank()) {
                transaction.mcc = null;
            }
        }
        return transaction;
    }
}
