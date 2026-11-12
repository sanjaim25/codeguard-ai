package com.codeguard.review;

import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface ReviewJobStore extends JpaRepository<ReviewJob, UUID> {
    Optional<ReviewJob> findByPullRequestIdAndCommitSha(UUID pullRequestId, String commitSha);
    List<ReviewJob> findByPullRequestIdOrderByCreatedAtDesc(UUID pullRequestId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ReviewJob j where j.id = :id")
    Optional<ReviewJob> lockById(UUID id);
    @Query("select j.id from ReviewJob j where (j.status = com.codeguard.review.ReviewStatus.PROCESSING and j.leaseUntil <= :now) or (j.status = com.codeguard.review.ReviewStatus.QUEUED and j.updatedAt <= :queuedBefore) order by j.updatedAt")
    List<UUID> recoverable(Instant now, Instant queuedBefore, Pageable page);
}
