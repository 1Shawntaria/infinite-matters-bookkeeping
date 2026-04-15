alter table notifications
    alter column organization_id drop not null;

alter table notifications
    alter column workflow_task_id drop not null;

alter table notifications
    add column category varchar(32) not null default 'WORKFLOW';

alter table notifications
    add column reference_type varchar(64);

alter table notifications
    add column reference_id varchar(128);

create index ix_notifications_user_created_at on notifications (user_id, created_at desc);
