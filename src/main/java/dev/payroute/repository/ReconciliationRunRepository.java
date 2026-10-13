package dev.payroute.repository;

import dev.payroute.domain.ReconciliationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {}
