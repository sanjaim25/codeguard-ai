CREATE TABLE github_installations (
    id UUID PRIMARY KEY,
    github_installation_id BIGINT NOT NULL UNIQUE,
    account_login VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE repositories (
    id UUID PRIMARY KEY,
    github_repository_id BIGINT NOT NULL UNIQUE,
    installation_id UUID NOT NULL REFERENCES github_installations(id),
    full_name VARCHAR(250) NOT NULL,
    private_repository BOOLEAN NOT NULL,
    language VARCHAR(100),
    review_enabled BOOLEAN NOT NULL DEFAULT true,
    cloud_ai_opt_in BOOLEAN NOT NULL DEFAULT false,
    publishing_enabled BOOLEAN NOT NULL DEFAULT false,
    auto_publish BOOLEAN NOT NULL DEFAULT false,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (NOT auto_publish OR publishing_enabled)
);
CREATE INDEX repositories_installation_idx ON repositories(installation_id);

CREATE TABLE repository_grants (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    permission VARCHAR(10) NOT NULL CHECK (permission IN ('READ', 'WRITE', 'ADMIN')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, repository_id)
);
CREATE INDEX repository_grants_repository_idx ON repository_grants(repository_id);
