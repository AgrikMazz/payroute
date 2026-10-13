package dev.payroute.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WebhookRequest(
        @NotBlank @Size(max = 120) String eventId,
        @NotBlank @Size(max = 120) String providerReference,
        @NotBlank @Size(max = 60) String eventType
) {}
