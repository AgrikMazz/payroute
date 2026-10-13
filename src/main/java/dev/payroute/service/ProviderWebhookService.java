package dev.payroute.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.payroute.api.ApiException;
import dev.payroute.api.WebhookRequest;
import dev.payroute.api.WebhookResponse;
import dev.payroute.domain.Payment;
import dev.payroute.domain.PaymentAttempt;
import dev.payroute.domain.PaymentStatus;
import dev.payroute.domain.ProviderConfig;
import dev.payroute.domain.WebhookEvent;
import dev.payroute.repository.PaymentRepository;
import dev.payroute.repository.PaymentAttemptRepository;
import dev.payroute.repository.ProviderConfigRepository;
import dev.payroute.repository.WebhookEventRepository;
import dev.payroute.security.WebhookSignatures;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

@Service
public class ProviderWebhookService {
    private final ProviderConfigRepository providers;
    private final WebhookEventRepository events;
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final LedgerService ledger;
    private final DeliveryService delivery;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String secret;
    private final MeterRegistry meters;
    private final ProviderHealthCache healthCache;

    public ProviderWebhookService(ProviderConfigRepository providers, WebhookEventRepository events, PaymentRepository payments,
                                  PaymentAttemptRepository attempts, LedgerService ledger, DeliveryService delivery, ObjectMapper mapper, Clock clock,
                                  ProviderHealthCache healthCache, @Value("${payroute.security.webhook-secret}") String secret, MeterRegistry meters) {
        this.providers = providers; this.events = events; this.payments = payments; this.attempts = attempts; this.ledger = ledger;
        this.delivery = delivery; this.mapper = mapper; this.clock = clock; this.healthCache = healthCache;
        this.secret = secret; this.meters = meters;
    }

    @Transactional
    public WebhookResponse accept(String providerCode, String signature, String rawBody) {
        ProviderConfig provider = providers.findById(providerCode)
                .orElseThrow(() -> ApiException.notFound("Sandbox provider was not found."));
        if (!WebhookSignatures.verify(secret, rawBody, signature)) {
            meters.counter("payroute.webhooks.rejected", "reason", "signature").increment();
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Webhook signature is invalid.");
        }
        WebhookRequest request;
        try { request = mapper.readValue(rawBody, WebhookRequest.class); }
        catch (JsonProcessingException e) { throw ApiException.badRequest("Webhook body must contain eventId, providerReference, and eventType."); }
        if (request.eventId() == null || request.eventId().isBlank() || request.eventId().length() > 120
                || request.providerReference() == null || request.providerReference().isBlank() || request.providerReference().length() > 120
                || request.eventType() == null || request.eventType().isBlank() || request.eventType().length() > 60)
            throw ApiException.badRequest("Webhook eventId, providerReference, and eventType must be non-empty and within their length limits.");
        if (!request.eventType().equals("payment.succeeded") && !request.eventType().equals("payment.failed"))
            throw ApiException.badRequest("Only payment.succeeded and payment.failed events are supported.");
        var duplicate = events.findByProviderCodeAndProviderEventId(providerCode, request.eventId());
        if (duplicate.isPresent()) {
            meters.counter("payroute.webhooks.duplicates").increment();
            Payment current = payments.findByProviderReference(request.providerReference()).orElse(null);
            return new WebhookResponse("DUPLICATE", current == null ? null : current.id, current == null ? null : current.status.name());
        }
        WebhookEvent event = new WebhookEvent(); event.id = UUID.randomUUID(); event.providerCode = providerCode;
        event.providerEventId = request.eventId(); event.providerReference = request.providerReference();
        event.eventType = request.eventType(); event.signatureValid = true; event.rawPayload = rawBody; event.receivedAt = clock.instant();
        Payment payment = payments.findByProviderReferenceForUpdate(request.providerReference()).orElse(null);
        if (payment == null) {
            event.processingStatus = "UNMATCHED"; events.save(event); meters.counter("payroute.webhooks.unmatched").increment();
            return new WebhookResponse("UNMATCHED", null, null);
        }
        if (!providerCode.equals(payment.selectedProviderCode)) {
            event.processingStatus = "PROVIDER_MISMATCH"; events.save(event);
            return new WebhookResponse("PROVIDER_MISMATCH", payment.id, payment.status.name());
        }
        if (payment.status != PaymentStatus.PROCESSING && payment.status != PaymentStatus.PENDING) {
            event.processingStatus = "IGNORED_TERMINAL"; events.save(event);
            meters.counter("payroute.webhooks.ignored").increment();
            return new WebhookResponse("IGNORED_TERMINAL", payment.id, payment.status.name());
        }
        if (request.eventType().equals("payment.succeeded")) {
            payment.status = PaymentStatus.SUCCEEDED; ledger.postCapture(payment); delivery.enqueue(payment, "payment.succeeded");
        } else {
            payment.status = PaymentStatus.FAILED; delivery.enqueue(payment, "payment.failed");
        }
        resolveTimedOutAttempt(payment, provider, request.eventType().equals("payment.succeeded"));
        payment.updatedAt = clock.instant(); payments.save(payment);
        event.processingStatus = "APPLIED"; events.save(event);
        meters.counter("payroute.webhooks.applied", "event", request.eventType()).increment();
        return new WebhookResponse("APPLIED", payment.id, payment.status.name());
    }

    private void resolveTimedOutAttempt(Payment payment, ProviderConfig provider, boolean succeeded) {
        PaymentAttempt timedOut = attempts.findAllByPaymentIdOrderByCreatedAtAsc(payment.id).stream()
                .filter(a -> a.providerCode.equals(provider.code) && a.outcome.equals("TIMEOUT") && a.resolvedOutcome == null)
                .reduce((first, second) -> second).orElse(null);
        if (timedOut == null) return;
        timedOut.resolvedOutcome = succeeded ? "CAPTURED" : "DECLINED";
        attempts.save(timedOut);
        provider.successRate = succeeded ? Math.min(0.9999, provider.successRate * 0.8 + 0.2) : Math.max(0.01, provider.successRate * 0.8);
        providers.save(provider); healthCache.update(provider);
    }
}
