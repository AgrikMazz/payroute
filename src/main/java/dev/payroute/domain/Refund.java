package dev.payroute.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds", uniqueConstraints = @UniqueConstraint(name = "uq_refund_idempotency", columnNames = {"payment_id", "idempotency_key"}))
public class Refund {
    @Id public UUID id;
    @Column(name = "payment_id", nullable = false) public UUID paymentId;
    @Column(name = "idempotency_key", nullable = false, length = 128) public String idempotencyKey;
    @Column(name = "amount_minor", nullable = false) public long amountMinor;
    @Column(nullable = false, length = 24) public String status;
    @Column(name = "provider_reference", length = 120) public String providerReference;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    public Refund() {}
}
