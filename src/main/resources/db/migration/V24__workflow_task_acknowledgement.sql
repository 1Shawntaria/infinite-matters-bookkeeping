ALTER TABLE workflow_tasks
    ADD COLUMN acknowledged_at TIMESTAMP NULL;

ALTER TABLE workflow_tasks
    ADD COLUMN acknowledged_by_user_id UUID NULL;

ALTER TABLE workflow_tasks
    ADD CONSTRAINT fk_workflow_tasks_acknowledged_by_user
        FOREIGN KEY (acknowledged_by_user_id) REFERENCES app_users(id);
