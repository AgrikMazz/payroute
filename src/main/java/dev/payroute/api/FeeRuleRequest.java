package dev.payroute.api;

import dev.payroute.domain.PaymentMethod;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record FeeRuleRequest(@NotBlank @Size(max = 120) String ruleName,
                             @Size(max = 80) String merchantId,
                             @NotNull PaymentMethod paymentMethod,
                             @Min(0) @Max(10000) int rateBps,
                             @PositiveOrZero long fixedFeeMinor,
                             Instant activeFrom,
                             Instant activeUntil,
                             Boolean enabled) {}
