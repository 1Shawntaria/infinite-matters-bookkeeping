ALTER TABLE notifications
    ADD COLUMN dead_letter_resolution_status VARCHAR(32);

ALTER TABLE notifications
    ADD COLUMN dead_letter_resolution_note VARCHAR(1000);

ALTER TABLE notifications
    ADD COLUMN dead_letter_resolved_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE notifications
    ADD COLUMN dead_letter_resolved_by_user_id UUID;

ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_dead_letter_resolved_by_user
    FOREIGN KEY (dead_letter_resolved_by_user_id) REFERENCES app_users(id);
