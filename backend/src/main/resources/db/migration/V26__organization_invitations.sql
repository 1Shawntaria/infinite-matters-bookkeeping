create table if not exists organization_invitations (
    id uuid primary key,
    organization_id uuid not null references organizations (id),
    invited_by_user_id uuid references app_users (id),
    accepted_by_user_id uuid references app_users (id),
    email varchar(320) not null,
    role varchar(32) not null,
    token_hash varchar(128) not null,
    status varchar(32) not null,
    expires_at timestamp not null,
    accepted_at timestamp,
    revoked_at timestamp,
    created_at timestamp not null
);

create unique index if not exists idx_organization_invitations_token_hash
    on organization_invitations (token_hash);

create index if not exists idx_organization_invitations_org_created_at
    on organization_invitations (organization_id, created_at desc);
