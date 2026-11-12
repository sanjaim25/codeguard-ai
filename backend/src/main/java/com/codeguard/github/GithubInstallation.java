package com.codeguard.github;

import com.codeguard.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "github_installations")
public class GithubInstallation extends BaseEntity {
    @Column(nullable = false, unique = true) private long githubInstallationId;
    @Column(nullable = false, length = 100) private String accountLogin;
    @Column(nullable = false) private boolean active = true;
    protected GithubInstallation() {}
    public GithubInstallation(long githubInstallationId, String accountLogin) {
        this.githubInstallationId = githubInstallationId; this.accountLogin = accountLogin;
    }
    public long getGithubInstallationId() { return githubInstallationId; }
    public String getAccountLogin() { return accountLogin; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
