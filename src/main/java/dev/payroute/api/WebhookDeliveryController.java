package dev.payroute.api;

import dev.payroute.service.DeliveryService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhook-deliveries")
public class WebhookDeliveryController {
    private final DeliveryService deliveries;
    public WebhookDeliveryController(DeliveryService deliveries) { this.deliveries = deliveries; }
    @GetMapping public List<WebhookDeliveryView> list() { return deliveries.list().stream().map(WebhookDeliveryView::of).toList(); }
    @PostMapping("/{id}/retry") public WebhookDeliveryView retry(@PathVariable UUID id) { return WebhookDeliveryView.of(deliveries.retryNow(id)); }
}
