package dev.payroute.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_items")
public class ReconciliationItem {
    @Id public UUID id;
    @Column(name = "run_id", nullable = false) public UUID runId;
    @Column(name = "provider_reference", nullable = false, length = 120) public String providerReference;
    @Column(name = "payment_id") public UUID paymentId;
    @Column(name = "expected_amount_minor") public Long expectedAmountMinor;
    @Column(name = "reported_amount_minor") public Long reportedAmountMinor;
    @Column(name = "expected_fee_minor") public Long expectedFeeMinor;
    @Column(name = "reported_fee_minor") public Long reportedFeeMinor;
    @Column(name = "discrepancy_type", nullable = false, length = 40) public String discrepancyType;
    @Column(nullable = false) public boolean resolved;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    public ReconciliationItem() {}
}
