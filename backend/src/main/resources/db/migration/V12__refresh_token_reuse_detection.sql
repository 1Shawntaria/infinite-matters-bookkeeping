alter table refresh_token_sessions
    add column revoked_reason varchar(255);

alter table refresh_token_sessions
    add column reuse_detected_at timestamp with time zone;
