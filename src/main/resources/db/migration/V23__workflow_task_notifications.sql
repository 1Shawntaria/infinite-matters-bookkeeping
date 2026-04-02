ALTER TABLE workflow_tasks
    ADD COLUMN notification_id UUID;

ALTER TABLE workflow_tasks
    ADD CONSTRAINT fk_workflow_tasks_notification
        FOREIGN KEY (notification_id) REFERENCES notifications(id);
