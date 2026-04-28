ALTER TABLE accounting_periods
    ADD COLUMN close_owner_user_id UUID NULL;

ALTER TABLE accounting_periods
    ADD COLUMN close_approver_user_id UUID NULL;

ALTER TABLE accounting_periods
    ADD COLUMN close_attestation_summary VARCHAR(2000) NULL;

ALTER TABLE accounting_periods
    ADD COLUMN close_attested_at TIMESTAMP WITH TIME ZONE NULL;

ALTER TABLE accounting_periods
    ADD COLUMN close_attested_by_user_id UUID NULL;

ALTER TABLE accounting_periods
    ADD CONSTRAINT fk_accounting_period_close_owner FOREIGN KEY (close_owner_user_id) REFERENCES app_users (id);

ALTER TABLE accounting_periods
    ADD CONSTRAINT fk_accounting_period_close_approver FOREIGN KEY (close_approver_user_id) REFERENCES app_users (id);

ALTER TABLE accounting_periods
    ADD CONSTRAINT fk_accounting_period_close_attested_by FOREIGN KEY (close_attested_by_user_id) REFERENCES app_users (id);
