package dev.payroute.repository;

import dev.payroute.domain.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    Optional<WebhookEvent> findByProviderCodeAndProviderEventId(String providerCode, String providerEventId);
}
