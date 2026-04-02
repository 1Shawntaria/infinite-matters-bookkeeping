create table account_close_states (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    financial_account_id uuid not null references financial_accounts(id),
    period_start date not null,
    period_end date not null,
    ready_for_close boolean not null,
    noted_at timestamp with time zone not null
);

create unique index ux_account_close_states_account_period
    on account_close_states (financial_account_id, period_start, period_end);
