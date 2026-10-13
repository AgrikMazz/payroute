package dev.payroute.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlement_records", uniqueConstraints = @UniqueConstraint(name = "uq_settlement_provider_reference", columnNames = {"provider_code", "provider_reference"}))
public class SettlementRecord {
    @Id public UUID id;
    @Column(name = "provider_code", nullable = false, length = 40) public String providerCode;
    @Column(name = "provider_reference", nullable = false, length = 120) public String providerReference;
    @Column(name = "payment_id") public UUID paymentId;
    @Column(name = "reported_amount_minor", nullable = false) public long reportedAmountMinor;
    @Column(name = "reported_fee_minor", nullable = false) public long reportedFeeMinor;
    @Column(nullable = false, length = 40) public String status;
    @Column(name = "ledger_posted", nullable = false) public boolean ledgerPosted;
    @Column(name = "imported_at", nullable = false) public Instant importedAt;
    public SettlementRecord() {}
}
