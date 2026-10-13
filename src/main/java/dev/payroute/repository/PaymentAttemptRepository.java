package dev.payroute.repository;

import dev.payroute.domain.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
    List<PaymentAttempt> findAllByProviderCode(String providerCode);
    List<PaymentAttempt> findAllByPaymentIdOrderByCreatedAtAsc(UUID paymentId);
}
