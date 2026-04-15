package com.infinitematters.bookkeeping.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JournalEntryLineRepository extends JpaRepository<JournalEntryLine, UUID> {
}
