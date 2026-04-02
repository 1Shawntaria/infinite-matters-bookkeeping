create table password_reset_tokens (
    id uuid primary key,
    user_id uuid not null references app_users(id),
    token_hash varchar(128) not null unique,
    expires_at timestamp with time zone not null,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone not null
);

create index idx_password_reset_tokens_user_id on password_reset_tokens(user_id);
