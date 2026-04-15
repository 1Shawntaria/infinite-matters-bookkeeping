package com.infinitematters.bookkeeping.service;

import com.infinitematters.bookkeeping.domain.Transaction;

import java.util.concurrent.ConcurrentHashMap;

public class IdempotencyStore {
    private final ConcurrentHashMap<Transaction, Boolean> seen = new ConcurrentHashMap<>();
    public boolean isNew    (Transaction t) { return seen.putIfAbsent(t, true) == null; }
}
