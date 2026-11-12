CREATE TABLE review_results (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL UNIQUE REFERENCES review_jobs(id),
    provider VARCHAR(40) NOT NULL,
    summary VARCHAR(8000) NOT NULL,
    diff_json TEXT NOT NULL,
    excluded_files INTEGER NOT NULL CHECK (excluded_files >= 0),
    incomplete BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE review_findings (
    id UUID PRIMARY KEY,
    result_id UUID NOT NULL REFERENCES review_results(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    file_path VARCHAR(500),
    line INTEGER CHECK (line > 0),
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW','INFO')),
    category VARCHAR(40) NOT NULL CHECK (category IN ('BUG','SECURITY','NULL_HANDLING','ERROR_HANDLING','RESOURCE_LEAK','MAINTAINABILITY','PERFORMANCE','CONCURRENCY','INPUT_VALIDATION')),
    suggested_fix VARCHAR(4000) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    explanation VARCHAR(4000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (line IS NULL OR file_path IS NOT NULL)
);
CREATE INDEX review_findings_result_idx ON review_findings(result_id);
CREATE INDEX review_findings_severity_idx ON review_findings(severity);
CREATE TABLE ai_usage_records (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES review_jobs(id),
    attempt INTEGER NOT NULL CHECK (attempt > 0),
    provider VARCHAR(40) NOT NULL,
    model VARCHAR(100) NOT NULL,
    input_tokens BIGINT NOT NULL CHECK (input_tokens >= 0),
    output_tokens BIGINT NOT NULL CHECK (output_tokens >= 0),
    measured_tokens BOOLEAN NOT NULL,
    estimated_cost_usd NUMERIC(14,8) CHECK (estimated_cost_usd >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(job_id, attempt)
);
CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    delivery_id VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(50) NOT NULL,
    body_sha256 VARCHAR(64) NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    job_id UUID REFERENCES review_jobs(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX webhook_deliveries_created_idx ON webhook_deliveries(created_at);
CREATE TABLE review_outbox (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES review_jobs(id),
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('REVIEW','DEAD_LETTER')),
    available_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(job_id, kind)
);
CREATE INDEX review_outbox_pending_idx ON review_outbox(available_at) WHERE sent_at IS NULL;
