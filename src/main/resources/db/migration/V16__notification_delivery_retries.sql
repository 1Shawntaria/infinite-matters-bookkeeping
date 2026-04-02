alter table notifications
    add column last_attempted_at timestamp with time zone;

alter table notifications
    add column attempt_count integer not null default 0;

alter table notifications
    add column last_error varchar(1000);
