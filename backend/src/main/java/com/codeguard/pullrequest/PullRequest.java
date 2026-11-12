package com.codeguard.pullrequest;

import com.codeguard.common.BaseEntity;
import com.codeguard.repository.CodeRepository;
import jakarta.persistence.*;

@Entity
@Table(name = "pull_requests", uniqueConstraints = @UniqueConstraint(columnNames = {"repository_id", "number"}))
public class PullRequest extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private CodeRepository repository;
    @Column(nullable = false) private int number;
    @Column(nullable = false, length = 500) private String title;
    @Column(nullable = false, length = 100) private String author;
    @Column(nullable = false, length = 64) private String headSha;
    @Column(nullable = false, length = 250) private String headRef;
    @Column(nullable = false, length = 250) private String baseRef;
    @Column(nullable = false, length = 20) private String state;
    @Version private long version;
    protected PullRequest() {}
    public PullRequest(CodeRepository repository, int number, String title, String author, String headSha, String headRef, String baseRef, String state) {
        if (number < 1) throw new IllegalArgumentException("Invalid pull request number");
        this.repository = repository; this.number = number;
        refresh(title, author, headSha, headRef, baseRef, state);
    }
    public void refresh(String title, String author, String headSha, String headRef, String baseRef, String state) {
        if (headSha == null || !headSha.matches("[a-fA-F0-9]{40,64}")) throw new IllegalArgumentException("Invalid head SHA");
        if (!java.util.Set.of("open", "closed").contains(state)) throw new IllegalArgumentException("Invalid pull request state");
        this.title = title; this.author = author; this.headSha = headSha;
        this.headRef = headRef; this.baseRef = baseRef; this.state = state;
    }
    public CodeRepository getRepository() { return repository; }
    public int getNumber() { return number; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getHeadSha() { return headSha; }
    public String getHeadRef() { return headRef; }
    public String getBaseRef() { return baseRef; }
    public String getState() { return state; }
}
