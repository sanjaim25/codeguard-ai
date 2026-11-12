CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    github_user_id BIGINT NOT NULL UNIQUE,
    login VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
