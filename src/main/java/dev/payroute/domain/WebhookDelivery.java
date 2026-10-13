package dev.payroute.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries", indexes = @Index(name = "idx_delivery_due", columnList = "status,next_attempt_at"))
public class WebhookDelivery {
    @Id public UUID id;
    @Column(name = "payment_id", nullable = false) public UUID paymentId;
    @Column(name = "event_type", nullable = false, length = 60) public String eventType;
    @Column(name = "payload", nullable = false, columnDefinition = "text") public String payload;
    @Column(name = "target_mode", nullable = false, length = 40) public String targetMode;
    @Column(nullable = false, length = 24) public String status;
    @Column(name = "attempt_count", nullable = false) public int attemptCount;
    @Column(name = "max_attempts", nullable = false) public int maxAttempts;
    @Column(name = "next_attempt_at", nullable = false) public Instant nextAttemptAt;
    @Column(name = "last_error", length = 300) public String lastError;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "delivered_at") public Instant deliveredAt;
    public WebhookDelivery() {}
}
