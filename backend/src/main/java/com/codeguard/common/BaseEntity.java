package com.codeguard.common;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity {
    @Id protected UUID id = UUID.randomUUID();
    @Column(nullable = false, updatable = false) protected Instant createdAt = Instant.now();
    @Column(nullable = false) protected Instant updatedAt = Instant.now();
    @PreUpdate protected void updated() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
