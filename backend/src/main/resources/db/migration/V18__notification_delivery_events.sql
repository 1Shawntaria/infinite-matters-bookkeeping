alter table notifications
    add column delivery_state varchar(16) not null default 'PENDING';

update notifications
set delivery_state = case
    when status = 'SENT' then 'DELIVERED'
    when status = 'FAILED' then 'FAILED'
    else 'PENDING'
end;

create table notification_delivery_events (
    id uuid primary key,
    notification_id uuid not null references notifications(id),
    provider_name varchar(128) not null,
    provider_message_id varchar(255) not null,
    event_type varchar(64) not null,
    external_event_id varchar(255) unique,
    payload_summary varchar(1000),
    occurred_at timestamp with time zone not null,
    created_at timestamp with time zone not null
);

create index ix_notification_delivery_events_notification_id
    on notification_delivery_events (notification_id, occurred_at desc);
