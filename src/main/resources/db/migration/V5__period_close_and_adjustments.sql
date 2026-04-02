create table accounting_periods (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    period_start date not null,
    period_end date not null,
    status varchar(16) not null,
    closed_at timestamp with time zone,
    created_at timestamp with time zone not null
);

create unique index ux_accounting_periods_org_range
    on accounting_periods (organization_id, period_start, period_end);

alter table journal_entries
    alter column transaction_id drop not null;

alter table journal_entries
    add column entry_type varchar(32) not null default 'TRANSACTION';

alter table journal_entries
    add column adjustment_reason varchar(1000);
