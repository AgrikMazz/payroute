package dev.payroute.repository;

import dev.payroute.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
    Optional<Refund> findByPaymentIdAndIdempotencyKey(UUID paymentId, String idempotencyKey);
    List<Refund> findAllByPaymentId(UUID paymentId);
}
