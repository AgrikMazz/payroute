package dev.payroute.repository;

import dev.payroute.domain.ReconciliationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ReconciliationItemRepository extends JpaRepository<ReconciliationItem, UUID> {
    List<ReconciliationItem> findAllByRunIdOrderByCreatedAtAsc(UUID runId);
    long countByResolvedFalse();
}
