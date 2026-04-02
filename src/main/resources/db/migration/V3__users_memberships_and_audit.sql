create table app_users (
    id uuid primary key,
    email varchar(255) not null unique,
    full_name varchar(120) not null,
    created_at timestamp with time zone not null
);

create table organization_memberships (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    user_id uuid not null references app_users(id),
    role varchar(32) not null,
    created_at timestamp with time zone not null
);

create unique index ux_org_memberships_org_user
    on organization_memberships (organization_id, user_id);

create table audit_events (
    id uuid primary key,
    organization_id uuid references organizations(id),
    actor_user_id uuid references app_users(id),
    event_type varchar(64) not null,
    entity_type varchar(64) not null,
    entity_id varchar(64) not null,
    details varchar(2000) not null,
    created_at timestamp with time zone not null
);

create index ix_audit_events_org_created
    on audit_events (organization_id, created_at desc);
