package dev.payroute.api;

import java.util.UUID;

public record WebhookResponse(String outcome, UUID paymentId, String paymentStatus) {}
