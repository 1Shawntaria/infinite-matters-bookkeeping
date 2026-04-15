package com.infinitematters.bookkeeping.close;

import com.infinitematters.bookkeeping.accounts.FinancialAccount;
import com.infinitematters.bookkeeping.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "account_close_states")
public class AccountCloseState {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "financial_account_id", nullable = false)
    private FinancialAccount financialAccount;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "ready_for_close", nullable = false)
    private boolean readyForClose;

    @Column(name = "noted_at", nullable = false)
    private Instant notedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (notedAt == null) {
            notedAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public FinancialAccount getFinancialAccount() { return financialAccount; }
    public void setFinancialAccount(FinancialAccount financialAccount) { this.financialAccount = financialAccount; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public boolean isReadyForClose() { return readyForClose; }
    public void setReadyForClose(boolean readyForClose) { this.readyForClose = readyForClose; }
    public Instant getNotedAt() { return notedAt; }
    public void setNotedAt(Instant notedAt) { this.notedAt = notedAt; }
}
