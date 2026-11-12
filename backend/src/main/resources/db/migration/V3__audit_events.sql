CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES user_accounts(id) ON DELETE SET NULL,
    action VARCHAR(60) NOT NULL,
    resource_id UUID NOT NULL,
    detail VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_events_resource_created_idx ON audit_events(resource_id, created_at DESC);
