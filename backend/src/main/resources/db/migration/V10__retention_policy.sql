-- V10: Data retention policy support
ALTER TABLE repositories ADD COLUMN IF NOT EXISTS retention_days INTEGER;

CREATE TABLE IF NOT EXISTS cleanup_log (
  id         BIGSERIAL PRIMARY KEY,
  ran_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  table_name VARCHAR(80) NOT NULL,
  rows_deleted BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_cleanup_log_ran_at ON cleanup_log (ran_at DESC);
