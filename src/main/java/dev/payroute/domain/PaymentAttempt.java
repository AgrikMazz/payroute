package dev.payroute.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {
    @Id public UUID id;
    @Column(name = "payment_id", nullable = false) public UUID paymentId;
    @Column(name = "provider_code", nullable = false, length = 40) public String providerCode;
    @Column(nullable = false, length = 24) public String outcome;
    @Column(name = "resolved_outcome", length = 24) public String resolvedOutcome;
    @Column(name = "latency_ms", nullable = false) public long latencyMs;
    @Column(name = "provider_fee_minor", nullable = false) public long providerFeeMinor;
    @Column(name = "detail", length = 300) public String detail;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    public PaymentAttempt() {}
}
