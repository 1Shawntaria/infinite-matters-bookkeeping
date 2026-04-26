ALTER TABLE organizations
    ADD COLUMN invitation_ttl_days INTEGER NOT NULL DEFAULT 7;
