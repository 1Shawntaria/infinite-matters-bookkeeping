package com.infinitematters.bookkeeping.periods;

import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.users.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "accounting_periods")
public class AccountingPeriod {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountingPeriodStatus status;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_method", nullable = false, length = 16)
    private PeriodCloseMethod closeMethod;

    @Column(name = "override_reason", length = 1000)
    private String overrideReason;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "override_approved_by_user_id")
    private AppUser overrideApprovedByUser;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public AccountingPeriodStatus getStatus() {
        return status;
    }

    public void setStatus(AccountingPeriodStatus status) {
        this.status = status;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public PeriodCloseMethod getCloseMethod() {
        return closeMethod;
    }

    public void setCloseMethod(PeriodCloseMethod closeMethod) {
        this.closeMethod = closeMethod;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }

    public AppUser getOverrideApprovedByUser() {
        return overrideApprovedByUser;
    }

    public void setOverrideApprovedByUser(AppUser overrideApprovedByUser) {
        this.overrideApprovedByUser = overrideApprovedByUser;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
