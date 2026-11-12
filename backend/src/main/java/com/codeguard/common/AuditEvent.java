package com.codeguard.common;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent extends BaseEntity {
    private UUID actorId;
    @Column(nullable = false, length = 60) private String action;
    @Column(nullable = false) private UUID resourceId;
    @Column(nullable = false, length = 1000) private String detail;
    protected AuditEvent() {}
    public AuditEvent(UUID actorId, String action, UUID resourceId, String detail) {
        this.actorId = actorId; this.action = action; this.resourceId = resourceId; this.detail = detail;
    }
}
