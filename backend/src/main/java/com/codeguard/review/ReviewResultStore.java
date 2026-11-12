package com.codeguard.review;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReviewResultStore extends JpaRepository<ReviewResult, UUID> {
    Optional<ReviewResult> findByJobId(UUID jobId);
}
