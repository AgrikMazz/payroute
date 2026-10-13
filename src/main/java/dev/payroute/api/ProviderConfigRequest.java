package dev.payroute.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ProviderConfigRequest(@NotNull Boolean enabled, @Min(0) @Max(10000) int providerFeeBps,
                                    @PositiveOrZero long fixedFeeMinor) {}
