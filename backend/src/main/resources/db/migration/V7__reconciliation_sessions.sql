create table reconciliation_sessions (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    financial_account_id uuid not null references financial_accounts(id),
    period_start date not null,
    period_end date not null,
    opening_balance numeric(19, 2) not null,
    statement_ending_balance numeric(19, 2) not null,
    computed_ending_balance numeric(19, 2),
    status varchar(16) not null,
    completed_at timestamp with time zone,
    created_at timestamp with time zone not null
);

create unique index ux_reconciliation_sessions_account_period
    on reconciliation_sessions (financial_account_id, period_start, period_end);
