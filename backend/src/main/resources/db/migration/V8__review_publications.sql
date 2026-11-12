CREATE TABLE review_publications (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL UNIQUE REFERENCES review_jobs(id),
    requested_by UUID REFERENCES user_accounts(id),
    automatic BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SENDING', 'PUBLISHED', 'UNKNOWN', 'FAILED', 'CANCELLED')),
    sending_at TIMESTAMPTZ,
    github_review_id BIGINT CHECK (github_review_id > 0),
    failure_code VARCHAR(60),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX review_publications_pending_idx ON review_publications(status, created_at);
