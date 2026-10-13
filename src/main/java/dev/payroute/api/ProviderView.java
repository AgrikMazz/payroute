package dev.payroute.api;

import dev.payroute.domain.ProviderConfig;
import java.util.List;

public record ProviderView(String code, String displayName, boolean enabled, List<String> supportedMethods,
                           int providerFeeBps, long fixedFeeMinor, double successRate, long p95LatencyMs) {
    public static ProviderView of(ProviderConfig p) {
        return new ProviderView(p.code, p.displayName, p.enabled, List.of(p.supportedMethods.split(",")),
                p.providerFeeBps, p.fixedFeeMinor, p.successRate, p.p95LatencyMs);
    }
}
