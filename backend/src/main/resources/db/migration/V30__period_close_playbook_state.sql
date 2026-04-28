ALTER TABLE organizations
    ADD COLUMN require_template_completion_before_close BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE period_close_playbook_items (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    template_item_id UUID NOT NULL,
    period_month VARCHAR(7) NOT NULL,
    assignee_user_id UUID NULL,
    approver_user_id UUID NULL,
    completed_at TIMESTAMP WITH TIME ZONE NULL,
    completed_by_user_id UUID NULL,
    approved_at TIMESTAMP WITH TIME ZONE NULL,
    approved_by_user_id UUID NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_playbook_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_playbook_template FOREIGN KEY (template_item_id) REFERENCES organization_close_template_items (id),
    CONSTRAINT fk_playbook_assignee FOREIGN KEY (assignee_user_id) REFERENCES app_users (id),
    CONSTRAINT fk_playbook_approver FOREIGN KEY (approver_user_id) REFERENCES app_users (id),
    CONSTRAINT fk_playbook_completed_by FOREIGN KEY (completed_by_user_id) REFERENCES app_users (id),
    CONSTRAINT fk_playbook_approved_by FOREIGN KEY (approved_by_user_id) REFERENCES app_users (id),
    CONSTRAINT uk_playbook_month_template UNIQUE (organization_id, template_item_id, period_month)
);
