package dev.payroute.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fee_rules")
public class FeeRule {
    @Id public UUID id;
    @Column(name = "rule_name", nullable = false, length = 120) public String ruleName;
    @Column(name = "merchant_id", length = 80) public String merchantId;
    @Enumerated(EnumType.STRING) @Column(name = "payment_method", nullable = false, length = 24) public PaymentMethod paymentMethod;
    @Column(name = "rate_bps", nullable = false) public int rateBps;
    @Column(name = "fixed_fee_minor", nullable = false) public long fixedFeeMinor;
    @Column(name = "active_from", nullable = false) public Instant activeFrom;
    @Column(name = "active_until") public Instant activeUntil;
    @Column(nullable = false) public boolean enabled;
    public FeeRule() {}
}
