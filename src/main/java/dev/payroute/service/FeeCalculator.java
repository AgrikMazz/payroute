package dev.payroute.service;

import dev.payroute.api.ApiException;
import dev.payroute.domain.FeeRule;
import dev.payroute.domain.PaymentMethod;
import dev.payroute.repository.FeeRuleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;

@Service
public class FeeCalculator {
    private final FeeRuleRepository rules;
    private final Clock clock;
    public FeeCalculator(FeeRuleRepository rules, Clock clock) { this.rules = rules; this.clock = clock; }

    public long merchantFee(long amountMinor, PaymentMethod method, String merchantId) {
        Instant now = clock.instant();
        FeeRule selected = rules.findAll().stream()
                .filter(r -> r.enabled && r.paymentMethod == method && !r.activeFrom.isAfter(now)
                        && (r.activeUntil == null || r.activeUntil.isAfter(now))
                        && (r.merchantId == null || r.merchantId.equals(merchantId)))
                .sorted(Comparator.comparing((FeeRule r) -> r.merchantId != null).reversed()
                        .thenComparing(r -> r.activeFrom, Comparator.reverseOrder()))
                .findFirst().orElse(null);
        if (selected == null) return 0;
        long variable = BigDecimal.valueOf(amountMinor).multiply(BigDecimal.valueOf(selected.rateBps))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact();
        long total = Math.addExact(variable, selected.fixedFeeMinor);
        if (total > amountMinor) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Configured merchant fee exceeds the payment amount.");
        return total;
    }
}
