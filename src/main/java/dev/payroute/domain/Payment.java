package dev.payroute.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(name = "uq_payment_idempotency", columnNames = {"merchant_id", "idempotency_key"}))
public class Payment {
    @Id public UUID id;
    @Column(name = "merchant_id", nullable = false, length = 80) public String merchantId;
    @Column(name = "idempotency_key", nullable = false, length = 128) public String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64) public String requestFingerprint;
    @Column(name = "amount_minor", nullable = false) public long amountMinor;
    @Column(nullable = false, length = 3) public String currency;
    @Enumerated(EnumType.STRING) @Column(name = "payment_method", nullable = false, length = 24) public PaymentMethod paymentMethod;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) public PaymentStatus status;
    @Column(name = "selected_provider_code", length = 40) public String selectedProviderCode;
    @Column(name = "provider_reference", length = 120) public String providerReference;
    @Column(name = "merchant_fee_minor", nullable = false) public long merchantFeeMinor;
    @Column(name = "provider_fee_minor", nullable = false) public long providerFeeMinor;
    @Column(name = "avoided_provider_fee_minor", nullable = false) public long avoidedProviderFeeMinor;
    @Column(name = "route_score") public Double routeScore;
    @Column(name = "route_reason", length = 500) public String routeReason;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;
    @Version public long version;

    public Payment() {}
}
