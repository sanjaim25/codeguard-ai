package com.codeguard.github;

public class GithubApiException extends RuntimeException {
    private final int status;
    private final boolean retryable;
    private final long retryAfterSeconds;
    public GithubApiException(int status, boolean retryable, long retryAfterSeconds) {
        super("GitHub request could not be completed");
        this.status = status; this.retryable = retryable; this.retryAfterSeconds = retryAfterSeconds;
    }
    public int status() { return status; }
    public boolean retryable() { return retryable; }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}
