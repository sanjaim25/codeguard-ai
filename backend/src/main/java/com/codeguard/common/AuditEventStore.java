package com.codeguard.common;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AuditEventStore extends JpaRepository<AuditEvent, UUID> {}
