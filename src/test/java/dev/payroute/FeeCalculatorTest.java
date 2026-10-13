package dev.payroute;

import dev.payroute.domain.FeeRule;
import dev.payroute.domain.PaymentMethod;
import dev.payroute.repository.FeeRuleRepository;
import dev.payroute.service.FeeCalculator;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FeeCalculatorTest {
    @Test
    void merchantSpecificActiveRuleOverridesDefaultAndRoundsInPaise() {
        Instant now = Instant.parse("2026-10-13T00:00:00Z");
        FeeRule fallback = rule(null, 250, 0, now.minusSeconds(100));
        FeeRule merchant = rule("merchant-42", 125, 10, now.minusSeconds(50));
        var repository = mock(FeeRuleRepository.class);
        when(repository.findAll()).thenReturn(List.of(fallback, merchant));
        var calculator = new FeeCalculator(repository, Clock.fixed(now, ZoneOffset.UTC));

        assertThat(calculator.merchantFee(1_000, PaymentMethod.CARD, "merchant-42")).isEqualTo(23);
        assertThat(calculator.merchantFee(1_000, PaymentMethod.CARD, "merchant-other")).isEqualTo(25);
    }

    private FeeRule rule(String merchant, int rate, long fixed, Instant start) {
        FeeRule r = new FeeRule(); r.merchantId = merchant; r.paymentMethod = PaymentMethod.CARD;
        r.rateBps = rate; r.fixedFeeMinor = fixed; r.activeFrom = start; r.enabled = true; return r;
    }
}
