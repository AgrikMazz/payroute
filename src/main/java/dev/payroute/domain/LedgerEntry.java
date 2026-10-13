package dev.payroute.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries", indexes = @Index(name = "idx_ledger_payment", columnList = "payment_id"))
public class LedgerEntry {
    @Id public UUID id;
    @Column(name = "transfer_id", nullable = false) public UUID transferId;
    @Column(name = "payment_id", nullable = false) public UUID paymentId;
    @Column(name = "account_code", nullable = false, length = 80) public String accountCode;
    @Column(nullable = false, length = 8) public String direction;
    @Column(name = "amount_minor", nullable = false) public long amountMinor;
    @Column(nullable = false, length = 3) public String currency;
    @Column(name = "entry_type", nullable = false, length = 32) public String entryType;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    public LedgerEntry() {}
}
