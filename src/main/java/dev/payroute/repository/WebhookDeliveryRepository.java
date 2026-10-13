package dev.payroute.repository;

import dev.payroute.domain.WebhookDelivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    List<WebhookDelivery> findAllByStatusOrderByCreatedAtDesc(String status);
    List<WebhookDelivery> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(String status, Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from WebhookDelivery d where d.id = :id")
    java.util.Optional<WebhookDelivery> findByIdForUpdate(@Param("id") UUID id);
    long countByStatusIn(List<String> statuses);
}
