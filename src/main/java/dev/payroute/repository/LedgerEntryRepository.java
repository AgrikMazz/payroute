package dev.payroute.repository;

import dev.payroute.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findAllByPaymentIdOrderByCreatedAtAsc(UUID paymentId);
    List<LedgerEntry> findAllByTransferId(UUID transferId);
}
