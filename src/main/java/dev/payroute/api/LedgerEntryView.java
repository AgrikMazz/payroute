package dev.payroute.api;

import dev.payroute.domain.LedgerEntry;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryView(UUID id, UUID transferId, UUID paymentId, String accountCode, String direction,
                              long amountMinor, String currency, String entryType, Instant createdAt) {
    public static LedgerEntryView of(LedgerEntry e) { return new LedgerEntryView(e.id, e.transferId, e.paymentId,
            e.accountCode, e.direction, e.amountMinor, e.currency, e.entryType, e.createdAt); }
}
