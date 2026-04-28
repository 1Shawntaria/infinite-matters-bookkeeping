ALTER TABLE organizations
    ADD COLUMN minimum_signoff_count INTEGER NOT NULL DEFAULT 1;

ALTER TABLE organizations
    ADD COLUMN require_owner_signoff_before_close BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE organization_close_template_items (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    label VARCHAR(120) NOT NULL,
    guidance VARCHAR(500) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
