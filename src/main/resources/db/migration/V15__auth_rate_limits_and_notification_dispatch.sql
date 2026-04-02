create table auth_attempt_counters (
    id uuid primary key,
    action varchar(64) not null,
    subject_key varchar(255) not null,
    window_start timestamp with time zone not null,
    failure_count integer not null,
    last_failure_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create unique index ux_auth_attempt_counters_action_subject
    on auth_attempt_counters (action, subject_key);

create index ix_notifications_status_scheduled_for
    on notifications (status, scheduled_for);
