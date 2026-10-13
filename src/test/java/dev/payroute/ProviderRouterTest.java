package dev.payroute;

import dev.payroute.domain.PaymentMethod;
import dev.payroute.domain.ProviderConfig;
import dev.payroute.repository.ProviderConfigRepository;
import dev.payroute.service.ProviderHealthCache;
import dev.payroute.service.ProviderRouter;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProviderRouterTest {
    @Test
    void calculatesRealFeesIncludingFixedComponentAndAppliesPreferencePenalty() {
        ProviderConfig fixed = provider("fixed", 10, 50);
        ProviderConfig percentage = provider("percentage", 20, 0);
        var repository = mock(ProviderConfigRepository.class);
        var health = mock(ProviderHealthCache.class);
        when(repository.findAll()).thenReturn(List.of(fixed, percentage));
        when(health.get(fixed)).thenReturn(new ProviderHealthCache.Snapshot(0.99, 100));
        when(health.get(percentage)).thenReturn(new ProviderHealthCache.Snapshot(0.99, 100));
        var router = new ProviderRouter(repository, health, 0.45, 0.35, 0.15, 0.05);

        var routes = router.routes(10_000, PaymentMethod.UPI, List.of("fixed"));

        assertThat(routes).extracting(r -> r.provider().code).containsExactly("percentage", "fixed");
        assertThat(routes.get(0).providerFeeMinor()).isEqualTo(20);
        assertThat(routes.get(1).providerFeeMinor()).isEqualTo(60);
        assertThat(routes.get(0).avoidedFeeMinor()).isEqualTo(40);
    }

    private ProviderConfig provider(String code, int bps, long fixedFee) {
        ProviderConfig p = new ProviderConfig(); p.code = code; p.displayName = code; p.enabled = true;
        p.supportedMethods = "UPI"; p.providerFeeBps = bps; p.fixedFeeMinor = fixedFee;
        p.successRate = .99; p.p95LatencyMs = 100; return p;
    }
}
