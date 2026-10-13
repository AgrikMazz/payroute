package dev.payroute.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "payroute.webhooks", name = "dispatcher-enabled", havingValue = "true", matchIfMissing = true)
public class WebhookDeliveryScheduler {
    private final DeliveryService deliveries;
    public WebhookDeliveryScheduler(DeliveryService deliveries) { this.deliveries = deliveries; }
    @Scheduled(fixedDelayString = "${payroute.webhooks.poll-interval-ms:1000}")
    public void dispatch() { deliveries.dispatchDue(); }
}
