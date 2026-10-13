package dev.payroute.api;

import dev.payroute.domain.Refund;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(UUID id, UUID paymentId, long amountMinor, String status, String providerReference, Instant createdAt, boolean idempotentReplay) {
    public static RefundResponse of(Refund r, boolean replay) { return new RefundResponse(r.id, r.paymentId, r.amountMinor, r.status, r.providerReference, r.createdAt, replay); }
}
