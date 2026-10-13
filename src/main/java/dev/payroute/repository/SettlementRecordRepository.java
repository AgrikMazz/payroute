package dev.payroute.repository;

import dev.payroute.domain.SettlementRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementRecordRepository extends JpaRepository<SettlementRecord, UUID> {
    Optional<SettlementRecord> findByProviderCodeAndProviderReference(String providerCode, String providerReference);
    List<SettlementRecord> findAllByOrderByImportedAtDesc();
}
