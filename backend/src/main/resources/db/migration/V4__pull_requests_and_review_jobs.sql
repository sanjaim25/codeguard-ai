CREATE TABLE pull_requests (
    id UUID PRIMARY KEY,
    repository_id UUID NOT NULL REFERENCES repositories(id),
    number INTEGER NOT NULL CHECK (number > 0),
    title VARCHAR(500) NOT NULL,
    author VARCHAR(100) NOT NULL,
    head_sha VARCHAR(64) NOT NULL CHECK (head_sha ~ '^[a-fA-F0-9]{40,64}$'),
    head_ref VARCHAR(250) NOT NULL,
    base_ref VARCHAR(250) NOT NULL,
    state VARCHAR(20) NOT NULL CHECK (state IN ('open', 'closed')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(repository_id, number)
);
CREATE INDEX pull_requests_updated_idx ON pull_requests(updated_at DESC);

CREATE TABLE review_jobs (
    id UUID PRIMARY KEY,
    pull_request_id UUID NOT NULL REFERENCES pull_requests(id),
    commit_sha VARCHAR(64) NOT NULL CHECK (commit_sha ~ '^[a-fA-F0-9]{40,64}$'),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','QUEUED','PROCESSING','COMPLETED','FAILED','CANCELLED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    lease_until TIMESTAMPTZ,
    claim_token UUID,
    failure_code VARCHAR(60),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(pull_request_id, commit_sha),
    CHECK (status <> 'PROCESSING' OR (claim_token IS NOT NULL AND lease_until IS NOT NULL))
);
CREATE INDEX review_jobs_dispatch_idx ON review_jobs(status, available_at);
CREATE INDEX review_jobs_lease_idx ON review_jobs(lease_until) WHERE status = 'PROCESSING';
CREATE INDEX review_jobs_created_idx ON review_jobs(created_at DESC);
