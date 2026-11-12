package com.codeguard.github;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Fences authorization snapshots fetched outside transactions against known local revocations. */
@Component
public class GithubAccessFence {
    private final JdbcTemplate jdbc;
    public GithubAccessFence(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public long revision() {
        return jdbc.queryForObject("select revision from github_access_revision where id=1", Long.class);
    }
    /** Caller holds a transaction; the lock is shared by discovery and access-changing webhooks. */
    public void lock() {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", "github-discovery");
    }
    public void changed() {
        jdbc.update("update github_access_revision set revision=revision+1 where id=1");
    }
    public void requireUnchanged(long snapshotRevision) {
        if (revision() != snapshotRevision) throw new GithubApiException(409, true, 0);
    }
}
