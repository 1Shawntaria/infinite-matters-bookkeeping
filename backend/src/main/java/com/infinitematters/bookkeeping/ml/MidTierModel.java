package com.infinitematters.bookkeeping.ml;

import com.infinitematters.bookkeeping.domain.CategorizationResult;
import com.infinitematters.bookkeeping.domain.Transaction;

public interface MidTierModel { CategorizationResult predict(Transaction t); }
