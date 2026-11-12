package com.codeguard.repository;

import com.codeguard.common.BaseEntity;
import com.codeguard.github.GithubInstallation;
import jakarta.persistence.*;

@Entity
@Table(name = "repositories")
public class CodeRepository extends BaseEntity {
    @Column(nullable = false, unique = true) private long githubRepositoryId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private GithubInstallation installation;
    @Column(nullable = false, length = 250) private String fullName;
    @Column(nullable = false) private boolean privateRepository;
    @Column(nullable = false) private boolean active = true;
    @Column(length = 100) private String language;
    @Column(nullable = false) private boolean reviewEnabled = true;
    @Column(nullable = false) private boolean cloudAiOptIn;
    @Column(nullable = false) private boolean publishingEnabled;
    @Column(nullable = false) private boolean autoPublish;
    @Version private long version;
    protected CodeRepository() {}
    public CodeRepository(long githubRepositoryId, GithubInstallation installation, String fullName, boolean privateRepository, String language) {
        this.githubRepositoryId = githubRepositoryId; this.installation = installation;
        updateMetadata(fullName, privateRepository, language);
    }
    public void updateMetadata(String fullName, boolean privateRepository, String language) {
        this.fullName = fullName; this.privateRepository = privateRepository; this.language = language;
    }
    public void reconnect(GithubInstallation installation) {
        if (!this.installation.getId().equals(installation.getId())) updatePolicy(true, false, false, false);
        this.installation = installation; this.active = true;
    }
    public void deactivate() { active = false; }
    public boolean isActive() { return active; }
    public void updatePolicy(boolean reviewEnabled, boolean cloudAiOptIn, boolean publishingEnabled, boolean autoPublish) {
        if (autoPublish && !publishingEnabled) throw new IllegalArgumentException("Automatic publishing requires publishing to be enabled");
        this.reviewEnabled = reviewEnabled; this.cloudAiOptIn = cloudAiOptIn;
        this.publishingEnabled = publishingEnabled; this.autoPublish = autoPublish;
    }
    public long getGithubRepositoryId() { return githubRepositoryId; }
    public GithubInstallation getInstallation() { return installation; }
    public String getFullName() { return fullName; }
    public boolean isPrivateRepository() { return privateRepository; }
    public String getLanguage() { return language; }
    public boolean isReviewEnabled() { return reviewEnabled; }
    public boolean isCloudAiOptIn() { return cloudAiOptIn; }
    public boolean isPublishingEnabled() { return publishingEnabled; }
    public boolean isAutoPublish() { return autoPublish; }
}
