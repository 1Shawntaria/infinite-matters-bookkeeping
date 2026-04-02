ALTER TABLE notifications
    ADD COLUMN recipient_email VARCHAR(320);

ALTER TABLE notifications
    ADD COLUMN dead_letter_resolution_reason_code VARCHAR(64);
