alter table workflow_tasks
    add column assigned_to_user_id uuid references app_users(id);

alter table workflow_tasks
    add column related_period_start date;

alter table workflow_tasks
    add column related_period_end date;

alter table workflow_tasks
    add column resolution_comment varchar(1000);

alter table workflow_tasks
    add column resolved_by_user_id uuid references app_users(id);

alter table workflow_tasks
    add column resolved_at timestamp with time zone;

alter table accounting_periods
    add column close_method varchar(16) not null default 'CHECKLIST';

alter table accounting_periods
    add column override_reason varchar(1000);

alter table accounting_periods
    add column override_approved_by_user_id uuid references app_users(id);
