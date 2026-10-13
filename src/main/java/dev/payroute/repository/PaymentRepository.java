package dev.payroute.repository;

import dev.payroute.domain.Payment;
import dev.payroute.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByMerchantIdAndIdempotencyKey(String merchantId, String idempotencyKey);
    Optional<Payment> findByProviderReference(String providerReference);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") java.util.UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.providerReference = :reference")
    Optional<Payment> findByProviderReferenceForUpdate(@Param("reference") String reference);
    List<Payment> findAllByStatusIn(Collection<PaymentStatus> statuses);
}
