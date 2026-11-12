CREATE TABLE github_access_revision (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    revision BIGINT NOT NULL CHECK (revision >= 0)
);
INSERT INTO github_access_revision(id, revision) VALUES (1, 0);
