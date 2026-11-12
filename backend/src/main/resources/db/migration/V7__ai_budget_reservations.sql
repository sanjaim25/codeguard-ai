CREATE TABLE ai_budget_reservations (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES review_jobs(id),
    attempt INTEGER NOT NULL CHECK (attempt > 0),
    reserved_usd NUMERIC(14,8) NOT NULL CHECK (reserved_usd >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(job_id, attempt)
);
CREATE INDEX review_jobs_recovery_idx ON review_jobs(status, lease_until);
