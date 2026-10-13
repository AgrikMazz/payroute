package dev.payroute.api;

import dev.payroute.domain.SettlementRecord;
import java.time.Instant;
import java.util.UUID;

public record SettlementRecordView(UUID id, String providerCode, String providerReference, UUID paymentId,
                                   long reportedAmountMinor, long reportedFeeMinor, String status,
                                   boolean ledgerPosted, Instant importedAt) {
    public static SettlementRecordView of(SettlementRecord r) { return new SettlementRecordView(r.id, r.providerCode,
            r.providerReference, r.paymentId, r.reportedAmountMinor, r.reportedFeeMinor, r.status, r.ledgerPosted, r.importedAt); }
}
