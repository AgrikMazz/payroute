package dev.payroute.service;

import dev.payroute.api.ApiException;
import dev.payroute.api.CreateRefundRequest;
import dev.payroute.api.RefundResponse;
import dev.payroute.domain.Payment;
import dev.payroute.domain.PaymentStatus;
import dev.payroute.domain.Refund;
import dev.payroute.repository.PaymentRepository;
import dev.payroute.repository.RefundRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

@Service
public class RefundService {
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final LedgerService ledger;
    private final DeliveryService delivery;
    private final Clock clock;
    private final MeterRegistry meters;
    public RefundService(PaymentRepository payments, RefundRepository refunds, LedgerService ledger, DeliveryService delivery, Clock clock, MeterRegistry meters) {
        this.payments = payments; this.refunds = refunds; this.ledger = ledger; this.delivery = delivery; this.clock = clock; this.meters = meters;
    }

    @Transactional
    public RefundResponse refund(UUID paymentId, String key, CreateRefundRequest request) {
        if (key == null || key.isBlank() || key.length() > 128) throw ApiException.badRequest("Idempotency-Key must contain 1 to 128 characters.");
        Refund existing = refunds.findByPaymentIdAndIdempotencyKey(paymentId, key).orElse(null);
        if (existing != null) {
            if (request.amountMinor() != null && request.amountMinor() != existing.amountMinor)
                throw ApiException.conflict("This refund idempotency key was already used with a different amount.");
            return RefundResponse.of(existing, true);
        }
        Payment p = payments.findByIdForUpdate(paymentId).orElseThrow(() -> ApiException.notFound("Payment was not found."));
        if (p.status != PaymentStatus.SUCCEEDED && p.status != PaymentStatus.PARTIALLY_REFUNDED)
            throw new ApiException(HttpStatus.CONFLICT, "Only a successful, not fully refunded payment can be refunded.");
        long alreadyRefunded = refunds.findAllByPaymentId(paymentId).stream().mapToLong(r -> r.amountMinor).sum();
        long amount = request.amountMinor() == null ? p.amountMinor - alreadyRefunded : request.amountMinor();
        if (amount <= 0 || amount > p.amountMinor - alreadyRefunded)
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Refund amount exceeds the remaining refundable amount.");
        Refund r = new Refund(); r.id = UUID.randomUUID(); r.paymentId = p.id; r.idempotencyKey = key;
        r.amountMinor = amount; r.status = "SUCCEEDED"; r.providerReference = "sim_refund_" + r.id.toString().replace("-", "").substring(0, 16);
        r.createdAt = clock.instant(); refunds.save(r);
        ledger.postRefund(p, amount);
        p.status = alreadyRefunded + amount == p.amountMinor ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        p.updatedAt = clock.instant(); payments.save(p);
        delivery.enqueue(p, "payment.refunded");
        meters.counter("payroute.refunds.completed").increment();
        return RefundResponse.of(r, false);
    }
}
