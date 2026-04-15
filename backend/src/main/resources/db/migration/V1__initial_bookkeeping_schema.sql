create table organizations (
    id uuid primary key,
    name varchar(120) not null,
    plan_tier varchar(32) not null,
    timezone varchar(64) not null,
    created_at timestamp with time zone not null
);

create table financial_accounts (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    name varchar(120) not null,
    account_type varchar(32) not null,
    institution_name varchar(120),
    currency varchar(3) not null,
    active boolean not null,
    created_at timestamp with time zone not null
);

create table bookkeeping_transactions (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    financial_account_id uuid not null references financial_accounts(id),
    external_id varchar(120),
    transaction_date date not null,
    posted_date date,
    amount numeric(19, 2) not null,
    currency varchar(3) not null,
    merchant varchar(255),
    memo varchar(500),
    mcc varchar(12),
    source_type varchar(32) not null,
    source_fingerprint varchar(255) not null,
    status varchar(32) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create unique index ux_transaction_source_fingerprint
    on bookkeeping_transactions (organization_id, source_fingerprint);

create table categorization_decisions (
    id uuid primary key,
    transaction_id uuid not null references bookkeeping_transactions(id),
    proposed_category varchar(64) not null,
    final_category varchar(64),
    route varchar(32) not null,
    confidence_score double precision not null,
    confidence_reason varchar(500) not null,
    explanation varchar(1000) not null,
    status varchar(32) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table workflow_tasks (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    transaction_id uuid references bookkeeping_transactions(id),
    task_type varchar(32) not null,
    title varchar(200) not null,
    description varchar(1000) not null,
    due_date date,
    status varchar(32) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index ix_workflow_tasks_org_status
    on workflow_tasks (organization_id, status);
