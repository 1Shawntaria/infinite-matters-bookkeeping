create table journal_entries (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    transaction_id uuid not null references bookkeeping_transactions(id),
    entry_date date not null,
    description varchar(500) not null,
    created_at timestamp with time zone not null
);

create unique index ux_journal_entries_transaction
    on journal_entries (transaction_id);

create table journal_entry_lines (
    id uuid primary key,
    journal_entry_id uuid not null references journal_entries(id),
    line_order integer not null,
    account_code varchar(64) not null,
    account_name varchar(120) not null,
    entry_side varchar(6) not null,
    amount numeric(19, 2) not null
);

create index ix_categorization_decisions_transaction_status
    on categorization_decisions (transaction_id, status);
