alter table workflow_tasks
    add column if not exists snoozed_until date;
