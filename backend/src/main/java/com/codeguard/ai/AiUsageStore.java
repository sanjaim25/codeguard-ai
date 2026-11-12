package com.codeguard.ai;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AiUsageStore extends JpaRepository<AiUsage, UUID> {
    List<AiUsage> findByJobIdOrderByCreatedAtAsc(UUID jobId);
    boolean existsByJobIdAndAttempt(UUID jobId, int attempt);
}
