package com.codeguard.publishing;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface PublicationStore extends JpaRepository<Publication, UUID> {
    Optional<Publication> findByJobId(UUID jobId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Publication p where p.id = :id")
    Optional<Publication> lockById(UUID id);
    @Query("select p.id from Publication p where p.status in (com.codeguard.publishing.Publication.Status.PENDING, com.codeguard.publishing.Publication.Status.SENDING) order by p.createdAt")
    List<UUID> actionable(Pageable page);
}
