package com.infinitematters.bookkeeping.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public class Transaction {

    public String id;
    public LocalDate date;
    public String merchant;
    public String memo;
    public BigDecimal amount;
    public String mcc;

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction t)) return false;
        return Objects.equals(id, t.id) &&
                Objects.equals(amount, t.amount) &&
                Objects.equals(date, t.date);
    }
    @Override public int hashCode() { return Objects.hash(id, amount, date);
    }
}
