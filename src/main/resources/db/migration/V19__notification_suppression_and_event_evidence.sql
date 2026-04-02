CREATE TABLE notification_suppressions (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    provider_name VARCHAR(128) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    source_notification_id UUID,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_event_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_notification_suppressions_source_notification
        FOREIGN KEY (source_notification_id) REFERENCES notifications(id)
);

CREATE UNIQUE INDEX uk_notification_suppressions_email_provider_active
    ON notification_suppressions(email, provider_name, active);

ALTER TABLE notification_delivery_events
    ADD COLUMN raw_payload VARCHAR(4000);

ALTER TABLE notification_delivery_events
    ADD COLUMN verification_method VARCHAR(64);

ALTER TABLE notification_delivery_events
    ADD COLUMN verification_reference VARCHAR(255);
