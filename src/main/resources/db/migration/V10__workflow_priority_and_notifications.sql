alter table workflow_tasks
    add column priority varchar(16) not null default 'MEDIUM';

create table notifications (
    id uuid primary key,
    organization_id uuid not null references organizations(id),
    workflow_task_id uuid not null references workflow_tasks(id),
    user_id uuid references app_users(id),
    channel varchar(16) not null,
    status varchar(16) not null,
    message varchar(1000) not null,
    scheduled_for timestamp with time zone not null,
    sent_at timestamp with time zone,
    created_at timestamp with time zone not null
);

create index ix_notifications_org_status on notifications (organization_id, status);
