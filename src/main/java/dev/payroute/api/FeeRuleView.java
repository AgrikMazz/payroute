package dev.payroute.api;

import dev.payroute.domain.FeeRule;
import java.time.Instant;
import java.util.UUID;

public record FeeRuleView(UUID id, String ruleName, String merchantId, String paymentMethod, int rateBps,
                          long fixedFeeMinor, Instant activeFrom, Instant activeUntil, boolean enabled) {
    public static FeeRuleView of(FeeRule r) { return new FeeRuleView(r.id, r.ruleName, r.merchantId,
            r.paymentMethod.name(), r.rateBps, r.fixedFeeMinor, r.activeFrom, r.activeUntil, r.enabled); }
}
