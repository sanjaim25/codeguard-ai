package com.codeguard.queue;
import java.util.*;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
public interface ReviewOutboxStore extends JpaRepository<ReviewOutbox, UUID> {
    Optional<ReviewOutbox> findByJobIdAndKind(UUID jobId, ReviewOutbox.Kind kind);
    @Query("select o from ReviewOutbox o where o.sentAt is null and o.availableAt <= :now order by o.availableAt")
    List<ReviewOutbox> pending(Instant now, Pageable page);
}
