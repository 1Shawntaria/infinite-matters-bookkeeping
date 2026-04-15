create table refresh_token_sessions (
    id uuid primary key,
    user_id uuid not null references app_users(id),
    token_hash varchar(128) not null unique,
    expires_at timestamp with time zone not null,
    last_used_at timestamp with time zone,
    revoked_at timestamp with time zone,
    replaced_by_session_id uuid,
    created_at timestamp with time zone not null
);

create index idx_refresh_token_sessions_user_id on refresh_token_sessions(user_id);
create index idx_refresh_token_sessions_expires_at on refresh_token_sessions(expires_at);
