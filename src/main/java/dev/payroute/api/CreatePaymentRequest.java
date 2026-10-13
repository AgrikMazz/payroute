package dev.payroute.api;

import dev.payroute.domain.PaymentMethod;
import dev.payroute.domain.SimulationScenario;
import jakarta.validation.constraints.*;
import java.util.List;

public record CreatePaymentRequest(
        @NotBlank @Size(max = 80) String merchantId,
        @Positive @Max(100_000_000_000L) long amountMinor,
        @Pattern(regexp = "INR") String currency,
        @NotNull PaymentMethod paymentMethod,
        @Size(max = 3) List<@Pattern(regexp = "[a-z0-9_]{3,40}") String> preferredProviders,
        SimulationScenario simulationScenario
) {}
