package dev.payroute.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SettlementReportRequest(
        @NotBlank @Size(max = 40) String providerCode,
        @NotEmpty @Size(max = 5000) List<@Valid Row> rows
) {
    public record Row(@NotBlank @Size(max = 120) String providerReference,
                      @NotNull @PositiveOrZero Long amountMinor,
                      @NotNull @PositiveOrZero Long feeMinor) {}
}
