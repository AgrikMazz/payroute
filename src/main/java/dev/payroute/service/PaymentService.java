package dev.payroute.service;

import dev.payroute.api.ApiException;
import dev.payroute.api.CreatePaymentRequest;
import dev.payroute.api.PaymentResponse;
import dev.payroute.domain.*;
import dev.payroute.repository.PaymentAttemptRepository;
import dev.payroute.repository.PaymentRepository;
import dev.payroute.repository.ProviderConfigRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final ProviderConfigRepository providers;
    private final ProviderRouter router;
    private final SimulatedGateway gateway;
    private final FeeCalculator fees;
    private final LedgerService ledger;
    private final DeliveryService delivery;
    private final RedisIdempotencyGuard guard;
    private final ProviderHealthCache healthCache;
    private final MeterRegistry meters;
    private final Clock clock;
    private final int maxAttempts;

    public PaymentService(PaymentRepository payments, PaymentAttemptRepository attempts, ProviderConfigRepository providers,
                         ProviderRouter router, SimulatedGateway gateway, FeeCalculator fees, LedgerService ledger,
                         DeliveryService delivery, RedisIdempotencyGuard guard, ProviderHealthCache healthCache,
                         MeterRegistry meters, Clock clock, @Value("${payroute.routing.max-attempts:2}") int maxAttempts) {
        this.payments = payments; this.attempts = attempts; this.providers = providers; this.router = router;
        this.gateway = gateway; this.fees = fees; this.ledger = ledger; this.delivery = delivery; this.guard = guard;
        this.healthCache = healthCache; this.meters = meters; this.clock = clock; this.maxAttempts = maxAttempts;
    }

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128)
            throw ApiException.badRequest("Idempotency-Key must contain 1 to 128 characters.");
        String currency = request.currency() == null ? "INR" : request.currency();
        if (!currency.equals("INR")) throw ApiException.badRequest("PayRoute currently supports INR sandbox transactions only.");
        SimulationScenario scenario = request.simulationScenario() == null ? SimulationScenario.SUCCESS : request.simulationScenario();
        String fingerprint = fingerprint(request, currency, scenario);
        var existing = payments.findByMerchantIdAndIdempotencyKey(request.merchantId(), idempotencyKey);
        if (existing.isPresent()) return replay(existing.get(), fingerprint);
        String lock = guard.acquire(request.merchantId(), idempotencyKey);
        if (lock == null) {
            existing = payments.findByMerchantIdAndIdempotencyKey(request.merchantId(), idempotencyKey);
            if (existing.isPresent()) return replay(existing.get(), fingerprint);
            throw ApiException.conflict("An identical payment request is still being processed. Retry with the same idempotency key.");
        }
        try {
            List<ProviderRouter.Route> routes = router.routes(request.amountMinor(), request.paymentMethod(), request.preferredProviders());
            Payment p = new Payment(); p.id = UUID.randomUUID(); p.merchantId = request.merchantId();
            p.idempotencyKey = idempotencyKey; p.requestFingerprint = fingerprint; p.amountMinor = request.amountMinor();
            p.currency = currency; p.paymentMethod = request.paymentMethod(); p.status = PaymentStatus.PROCESSING;
            p.merchantFeeMinor = fees.merchantFee(p.amountMinor, p.paymentMethod, p.merchantId);
            p.createdAt = clock.instant(); p.updatedAt = p.createdAt;
            payments.saveAndFlush(p);
            int attemptNumber = 0;
            int limit = Math.min(Math.max(1, maxAttempts), routes.size());
            for (ProviderRouter.Route route : routes.subList(0, limit)) {
                attemptNumber++;
                SimulatedGateway.Result result = gateway.charge(route.provider(), scenario, attemptNumber);
                PaymentAttempt attempt = new PaymentAttempt(); attempt.id = UUID.randomUUID(); attempt.paymentId = p.id;
                attempt.providerCode = route.provider().code; attempt.outcome = result.outcome().name();
                attempt.latencyMs = result.latencyMs(); attempt.providerFeeMinor = route.providerFeeMinor();
                attempt.detail = result.detail(); attempt.createdAt = clock.instant(); attempts.save(attempt);
                updateHealth(route.provider(), result);
                meters.counter("payroute.provider.attempts", "provider", route.provider().code, "outcome", result.outcome().name()).increment();
                if (result.outcome() == SimulatedGateway.Outcome.RETRYABLE_FAILURE) continue;
                p.selectedProviderCode = route.provider().code; p.routeScore = route.score(); p.routeReason = route.reason();
                p.providerFeeMinor = route.providerFeeMinor(); p.avoidedProviderFeeMinor = route.avoidedFeeMinor();
                p.providerReference = providerReference(route.provider().code, p.id);
                if (result.outcome() == SimulatedGateway.Outcome.CAPTURED) {
                    p.status = PaymentStatus.SUCCEEDED; ledger.postCapture(p); delivery.enqueue(p, "payment.succeeded");
                    meters.counter("payroute.payments.completed", "status", "SUCCEEDED").increment();
                } else if (result.outcome() == SimulatedGateway.Outcome.DECLINED) {
                    p.status = PaymentStatus.FAILED; delivery.enqueue(p, "payment.failed");
                    meters.counter("payroute.payments.completed", "status", "FAILED").increment();
                } else {
                    p.status = PaymentStatus.PROCESSING;
                    meters.counter("payroute.payments.timeouts").increment();
                }
                p.updatedAt = clock.instant(); payments.save(p);
                return response(p, false);
            }
            ProviderRouter.Route last = routes.get(limit - 1);
            p.status = PaymentStatus.FAILED; p.selectedProviderCode = last.provider().code;
            p.routeScore = last.score(); p.routeReason = last.reason(); p.providerFeeMinor = last.providerFeeMinor();
            p.providerReference = providerReference(last.provider().code, p.id);
            p.updatedAt = clock.instant(); payments.save(p); delivery.enqueue(p, "payment.failed");
            meters.counter("payroute.payments.completed", "status", "FAILED").increment();
            return response(p, false);
        } catch (DataIntegrityViolationException race) {
            throw ApiException.conflict("The idempotency key was used concurrently. Retry the request to retrieve the original payment.");
        } finally {
            guard.release(request.merchantId(), idempotencyKey, lock);
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID id) {
        Payment p = payments.findById(id).orElseThrow(() -> ApiException.notFound("Payment was not found."));
        return response(p, false);
    }

    private PaymentResponse replay(Payment p, String fingerprint) {
        if (!p.requestFingerprint.equals(fingerprint)) throw ApiException.conflict("This idempotency key was already used with a different request payload.");
        meters.counter("payroute.idempotency.replays").increment();
        return response(p, true);
    }
    private PaymentResponse response(Payment p, boolean replay) {
        return PaymentResponse.of(p, attempts.findAllByPaymentIdOrderByCreatedAtAsc(p.id), replay);
    }
    private String fingerprint(CreatePaymentRequest r, String currency, SimulationScenario scenario) {
        String input = r.merchantId() + "|" + r.amountMinor() + "|" + currency + "|" + r.paymentMethod()
                + "|" + String.join(",", r.preferredProviders() == null ? List.of() : r.preferredProviders()) + "|" + scenario;
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private String providerReference(String code, UUID id) { return "sim_" + code + "_" + id.toString().replace("-", "").substring(0, 16); }
    private void updateHealth(ProviderConfig provider, SimulatedGateway.Result result) {
        if (result.outcome() == SimulatedGateway.Outcome.CAPTURED) {
            provider.successRate = Math.min(0.9999, provider.successRate * 0.8 + 0.2);
        } else if (result.outcome() != SimulatedGateway.Outcome.TIMEOUT) {
            provider.successRate = Math.max(0.01, provider.successRate * 0.8);
        }
        provider.p95LatencyMs = Math.max(1, Math.round(provider.p95LatencyMs * 0.8 + result.latencyMs() * 0.2));
        providers.save(provider); healthCache.update(provider);
    }
}
