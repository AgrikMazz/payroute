package dev.payroute.api;

import dev.payroute.domain.ReconciliationItem;
import dev.payroute.domain.ReconciliationRun;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReconciliationResponse(UUID id, String providerCode, String status, int rowCount,
                                    int matchedCount, int discrepancyCount, Instant createdAt, List<Item> items) {
    public static ReconciliationResponse of(ReconciliationRun r, List<ReconciliationItem> items) {
        return new ReconciliationResponse(r.id, r.providerCode, r.status, r.rowCount, r.matchedCount,
                r.discrepancyCount, r.createdAt, items.stream().map(Item::of).toList());
    }
    public record Item(String providerReference, UUID paymentId, Long expectedAmountMinor, Long reportedAmountMinor,
                       Long expectedFeeMinor, Long reportedFeeMinor, String discrepancyType, boolean resolved) {
        static Item of(ReconciliationItem i) { return new Item(i.providerReference, i.paymentId, i.expectedAmountMinor,
                i.reportedAmountMinor, i.expectedFeeMinor, i.reportedFeeMinor, i.discrepancyType, i.resolved); }
    }
}
