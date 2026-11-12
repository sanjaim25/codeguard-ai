package com.codeguard.repository;

import com.codeguard.common.BaseEntity;
import com.codeguard.user.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "repository_grants", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "repository_id"}))
public class RepositoryGrant extends BaseEntity {
    public enum Permission { READ, WRITE, ADMIN }
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private UserAccount user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private CodeRepository repository;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Permission permission;
    @Column(nullable = false) private Instant expiresAt;
    protected RepositoryGrant() {}
    public RepositoryGrant(UserAccount user, CodeRepository repository, Permission permission, Instant expiresAt) {
        this.user = user; this.repository = repository; refresh(permission, expiresAt);
    }
    public void refresh(Permission permission, Instant expiresAt) { this.permission = permission; this.expiresAt = expiresAt; }
    public Permission getPermission() { return permission; }
    public boolean canWrite() { return permission == Permission.WRITE || permission == Permission.ADMIN; }
    public CodeRepository getRepository() { return repository; }
    public Instant getExpiresAt() { return expiresAt; }
}
