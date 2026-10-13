package dev.payroute.api;

import dev.payroute.service.ProviderWebhookService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
public class ProviderWebhookController {
    private final ProviderWebhookService webhooks;
    public ProviderWebhookController(ProviderWebhookService webhooks) { this.webhooks = webhooks; }
    @PostMapping(value = "/{providerCode}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public WebhookResponse receive(@PathVariable String providerCode, @RequestHeader(value = "X-Signature", required = false) String signature,
                                   @RequestBody String rawBody) {
        return webhooks.accept(providerCode, signature, rawBody);
    }
}
