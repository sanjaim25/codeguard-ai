package com.codeguard.ai;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AiBudgetStore extends JpaRepository<AiBudgetReservation, UUID> {
    boolean existsByJobIdAndAttempt(UUID jobId, int attempt);
    @Query("select coalesce(sum(r.reservedUsd), 0) from AiBudgetReservation r where r.jobId = :jobId")
    BigDecimal totalReserved(UUID jobId);
}
