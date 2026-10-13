package dev.payroute.api;

import dev.payroute.domain.Payment;
import dev.payroute.domain.PaymentAttempt;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentResponse(
        UUID id, String merchantId, long amountMinor, String currency, String paymentMethod,
        String status, String providerCode, String providerReference, long merchantFeeMinor,
        long providerFeeMinor, long avoidedProviderFeeMinor, Double routeScore, String routeReason,
        boolean idempotentReplay, Instant createdAt, List<AttemptView> attempts
) {
    public static PaymentResponse of(Payment p, List<PaymentAttempt> attempts, boolean replay) {
        return new PaymentResponse(p.id, p.merchantId, p.amountMinor, p.currency, p.paymentMethod.name(),
                p.status.name(), p.selectedProviderCode, p.providerReference, p.merchantFeeMinor,
                p.providerFeeMinor, p.avoidedProviderFeeMinor, p.routeScore, p.routeReason, replay,
                p.createdAt, attempts.stream().map(AttemptView::of).toList());
    }
    public record AttemptView(String providerCode, String outcome, String resolvedOutcome, long latencyMs, long providerFeeMinor, String detail) {
        static AttemptView of(PaymentAttempt a) { return new AttemptView(a.providerCode, a.outcome, a.resolvedOutcome, a.latencyMs, a.providerFeeMinor, a.detail); }
    }
}
