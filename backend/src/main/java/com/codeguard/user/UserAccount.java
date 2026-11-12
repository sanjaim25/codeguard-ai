package com.codeguard.user;

import com.codeguard.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "user_accounts")
public class UserAccount extends BaseEntity {
    @Column(nullable = false, unique = true) private long githubUserId;
    @Column(nullable = false, length = 100) private String login;
    @Column(length = 500) private String avatarUrl;
    protected UserAccount() {}
    public UserAccount(long githubUserId, String login, String avatarUrl) {
        this.githubUserId = githubUserId;
        updateProfile(login, avatarUrl);
    }
    public void updateProfile(String login, String avatarUrl) { this.login = login; this.avatarUrl = avatarUrl; }
    public long getGithubUserId() { return githubUserId; }
    public String getLogin() { return login; }
    public String getAvatarUrl() { return avatarUrl; }
}
