package com.codeguard.ai;

import com.codeguard.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Kept even after a failed/ambiguous HTTP call: a timeout does not prove the provider did not bill it. */
@Entity
@Table(name = "ai_budget_reservations", uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "attempt"}))
public class AiBudgetReservation extends BaseEntity {
    @Column(nullable = false) private UUID jobId;
    @Column(nullable = false) private int attempt;
    @Column(nullable = false, precision = 14, scale = 8) private BigDecimal reservedUsd;
    protected AiBudgetReservation() {}
    public AiBudgetReservation(UUID jobId, int attempt, BigDecimal reservedUsd) { this.jobId = jobId; this.attempt = attempt; this.reservedUsd = reservedUsd; }
    public BigDecimal getReservedUsd() { return reservedUsd; }
}
