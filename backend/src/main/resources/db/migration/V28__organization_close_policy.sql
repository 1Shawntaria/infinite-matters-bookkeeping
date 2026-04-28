ALTER TABLE organizations
    ADD COLUMN close_materiality_threshold NUMERIC(12, 2) NOT NULL DEFAULT 500.00;

ALTER TABLE organizations
    ADD COLUMN minimum_close_notes_required INTEGER NOT NULL DEFAULT 1;

ALTER TABLE organizations
    ADD COLUMN require_signoff_before_close BOOLEAN NOT NULL DEFAULT TRUE;
