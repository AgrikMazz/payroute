package dev.payroute.service;

import dev.payroute.api.ApiException;
import dev.payroute.domain.PaymentMethod;
import dev.payroute.domain.ProviderConfig;
import dev.payroute.repository.ProviderConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

@Service
public class ProviderRouter {
    public record Route(ProviderConfig provider, double score, long providerFeeMinor, long avoidedFeeMinor, String reason) {}
    private final ProviderConfigRepository providers;
    private final ProviderHealthCache health;
    private final double costWeight, reliabilityWeight, latencyWeight, preferenceWeight;

    public ProviderRouter(ProviderConfigRepository providers, ProviderHealthCache health,
                          @Value("${payroute.routing.cost-weight:0.45}") double costWeight,
                          @Value("${payroute.routing.reliability-weight:0.35}") double reliabilityWeight,
                          @Value("${payroute.routing.latency-weight:0.15}") double latencyWeight,
                          @Value("${payroute.routing.preference-weight:0.05}") double preferenceWeight) {
        this.providers = providers; this.health = health; this.costWeight = costWeight;
        this.reliabilityWeight = reliabilityWeight; this.latencyWeight = latencyWeight; this.preferenceWeight = preferenceWeight;
    }

    public List<Route> routes(long amountMinor, PaymentMethod method, List<String> preferred) {
        List<ProviderConfig> eligible = providers.findAll().stream().filter(p -> p.enabled
                && List.of(p.supportedMethods.split(",")).contains(method.name())).toList();
        if (eligible.isEmpty()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "No enabled payment provider supports " + method + ".");
        long highestFee = eligible.stream().mapToLong(p -> providerFee(amountMinor, p)).max().orElse(0);
        List<String> preferences = preferred == null ? List.of() : preferred;
        return eligible.stream().map(p -> {
            ProviderHealthCache.Snapshot snapshot = health.get(p);
            long fee = providerFee(amountMinor, p);
            double feeScore = highestFee == 0 ? 0 : (double) fee / highestFee;
            double reliabilityScore = 1.0 - snapshot.successRate();
            double latencyScore = Math.min(1.0, (double) snapshot.p95LatencyMs() / 1000.0);
            int rank = preferences.indexOf(p.code);
            double preferenceScore = preferences.isEmpty() ? 0 : rank < 0 ? 1 : (double) rank / Math.max(1, preferences.size() - 1);
            double score = costWeight * feeScore + reliabilityWeight * reliabilityScore
                    + latencyWeight * latencyScore + preferenceWeight * preferenceScore;
            return new Route(p, score, fee, Math.max(0, highestFee - fee),
                    "score = cost " + fmt(feeScore) + "×" + costWeight + " + failure risk " + fmt(reliabilityScore)
                            + "×" + reliabilityWeight + " + p95 latency " + snapshot.p95LatencyMs() + "ms×" + latencyWeight
                            + (preferences.isEmpty() ? "" : " + merchant preference rank " + (rank < 0 ? "unlisted" : rank)));
        }).sorted(Comparator.comparingDouble(Route::score).thenComparing(r -> r.provider().code)).toList();
    }

    public static long providerFee(long amountMinor, ProviderConfig provider) {
        long variable = BigDecimal.valueOf(amountMinor).multiply(BigDecimal.valueOf(provider.providerFeeBps))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact();
        return Math.addExact(variable, provider.fixedFeeMinor);
    }
    private static String fmt(double v) { return String.format(java.util.Locale.ROOT, "%.3f", v); }
}
