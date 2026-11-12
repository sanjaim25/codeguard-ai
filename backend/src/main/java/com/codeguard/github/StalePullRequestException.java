package com.codeguard.github;
public class StalePullRequestException extends RuntimeException {
    public StalePullRequestException() { super("The pull request is closed or its head commit changed"); }
}
