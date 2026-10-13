package dev.payroute.api;

import jakarta.validation.constraints.Positive;

public record CreateRefundRequest(@Positive Long amountMinor) {}
