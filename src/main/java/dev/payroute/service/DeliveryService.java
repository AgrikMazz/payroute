package dev.payroute.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.payroute.api.ApiException;
import dev.payroute.domain.Payment;
import dev.payroute.domain.WebhookDelivery;
import dev.payroute.repository.WebhookDeliveryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DeliveryService {
    private final WebhookDeliveryRepository deliveries;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String mode;
    private final int maxAttempts;
    private final long retryBaseSeconds;

    public DeliveryService(WebhookDeliveryRepository deliveries, ObjectMapper mapper, Clock clock,
                           @Value("${payroute.webhooks.delivery-mode:success}") String mode,
                           @Value("${payroute.webhooks.max-attempts:5}") int maxAttempts,
                           @Value("${payroute.webhooks.retry-base-seconds:2}") long retryBaseSeconds) {
        this.deliveries = deliveries; this.mapper = mapper; this.clock = clock; this.mode = mode;
        this.maxAttempts = maxAttempts; this.retryBaseSeconds = retryBaseSeconds;
    }

    public WebhookDelivery enqueue(Payment p, String eventType) {
        WebhookDelivery d = new WebhookDelivery(); d.id = UUID.randomUUID(); d.paymentId = p.id;
        d.eventType = eventType; d.targetMode = mode; d.status = "PENDING"; d.attemptCount = 0;
        d.maxAttempts = maxAttempts; d.nextAttemptAt = clock.instant(); d.createdAt = clock.instant();
        try { d.payload = mapper.writeValueAsString(Map.of("eventType", eventType, "paymentId", p.id,
                "merchantId", p.merchantId, "amountMinor", p.amountMinor, "currency", p.currency,
                "status", p.status.name(), "providerReference", p.providerReference)); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not serialize webhook payload", e); }
        return deliveries.save(d);
    }

    public List<WebhookDelivery> list() {
        List<WebhookDelivery> all = new ArrayList<>();
        for (String status : List.of("PENDING", "RETRYING", "DELIVERED", "DEAD")) all.addAll(deliveries.findAllByStatusOrderByCreatedAtDesc(status));
        all.sort(java.util.Comparator.comparing((WebhookDelivery d) -> d.createdAt).reversed());
        return all;
    }

    @Transactional
    public WebhookDelivery retryNow(UUID id) {
        WebhookDelivery d = deliveries.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Webhook delivery was not found."));
        if (d.status.equals("DELIVERED")) return d;
        d.status = "PENDING"; d.nextAttemptAt = clock.instant();
        return deliveries.save(d);
    }

    @Transactional
    public void dispatchDue() {
        Instant now = clock.instant();
        deliveries.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc("PENDING", now).forEach(d -> deliver(d.id, now));
        deliveries.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc("RETRYING", now).forEach(d -> deliver(d.id, now));
    }

    private void deliver(UUID id, Instant now) {
        WebhookDelivery d = deliveries.findByIdForUpdate(id).orElse(null);
        if (d == null || (!d.status.equals("PENDING") && !d.status.equals("RETRYING")) || d.nextAttemptAt.isAfter(now)) return;
        d.attemptCount++;
        boolean fail = d.targetMode.equals("always-fail") || (d.targetMode.equals("flaky-once") && d.attemptCount == 1);
        if (!fail) {
            d.status = "DELIVERED"; d.deliveredAt = now; d.lastError = null;
        } else {
            d.lastError = "Simulated receiver returned HTTP 503.";
            if (d.attemptCount >= d.maxAttempts) d.status = "DEAD";
            else {
                d.status = "RETRYING";
                long multiplier = 1L << Math.min(20, Math.max(0, d.attemptCount - 1));
                d.nextAttemptAt = now.plus(Duration.ofSeconds(Math.min(3600, retryBaseSeconds * multiplier)));
            }
        }
        deliveries.save(d);
    }
}
