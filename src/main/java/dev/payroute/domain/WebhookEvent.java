package dev.payroute.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events", uniqueConstraints = @UniqueConstraint(name = "uq_webhook_provider_event", columnNames = {"provider_code", "provider_event_id"}))
public class WebhookEvent {
    @Id public UUID id;
    @Column(name = "provider_code", nullable = false, length = 40) public String providerCode;
    @Column(name = "provider_event_id", nullable = false, length = 120) public String providerEventId;
    @Column(name = "provider_reference", nullable = false, length = 120) public String providerReference;
    @Column(name = "event_type", nullable = false, length = 60) public String eventType;
    @Column(name = "signature_valid", nullable = false) public boolean signatureValid;
    @Column(name = "processing_status", nullable = false, length = 24) public String processingStatus;
    @Column(name = "raw_payload", nullable = false, columnDefinition = "text") public String rawPayload;
    @Column(name = "received_at", nullable = false) public Instant receivedAt;
    public WebhookEvent() {}
}
