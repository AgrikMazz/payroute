package dev.payroute.api;

import dev.payroute.domain.WebhookDelivery;
import java.time.Instant;
import java.util.UUID;

public record WebhookDeliveryView(UUID id, UUID paymentId, String eventType, String targetMode, String status,
                                  int attemptCount, int maxAttempts, Instant nextAttemptAt, String lastError,
                                  Instant createdAt, Instant deliveredAt) {
    public static WebhookDeliveryView of(WebhookDelivery d) { return new WebhookDeliveryView(d.id, d.paymentId,
            d.eventType, d.targetMode, d.status, d.attemptCount, d.maxAttempts, d.nextAttemptAt,
            d.lastError, d.createdAt, d.deliveredAt); }
}
