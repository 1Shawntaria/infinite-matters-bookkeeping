package com.infinitematters.bookkeeping.ml;

import com.infinitematters.bookkeeping.domain.CategorizationResult;
import com.infinitematters.bookkeeping.domain.Transaction;

public interface PremiumModel { CategorizationResult predict(Transaction t);}
