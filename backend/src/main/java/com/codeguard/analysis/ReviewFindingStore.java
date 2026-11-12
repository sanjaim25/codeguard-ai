package com.codeguard.analysis;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReviewFindingStore extends JpaRepository<ReviewFinding, UUID> {
    List<ReviewFinding> findByResultIdOrderByCreatedAtAsc(UUID resultId);
}
