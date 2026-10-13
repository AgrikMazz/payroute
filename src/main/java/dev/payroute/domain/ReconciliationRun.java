package dev.payroute.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {
    @Id public UUID id;
    @Column(name = "provider_code", nullable = false, length = 40) public String providerCode;
    @Column(nullable = false, length = 24) public String status;
    @Column(name = "row_count", nullable = false) public int rowCount;
    @Column(name = "matched_count", nullable = false) public int matchedCount;
    @Column(name = "discrepancy_count", nullable = false) public int discrepancyCount;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    public ReconciliationRun() {}
}
