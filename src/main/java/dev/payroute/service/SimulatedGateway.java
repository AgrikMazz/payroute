package dev.payroute.service;

import dev.payroute.domain.ProviderConfig;
import dev.payroute.domain.SimulationScenario;
import org.springframework.stereotype.Component;

@Component
public class SimulatedGateway {
    public enum Outcome { CAPTURED, DECLINED, RETRYABLE_FAILURE, TIMEOUT }
    public record Result(Outcome outcome, long latencyMs, String detail) {}

    public Result charge(ProviderConfig provider, SimulationScenario scenario, int attemptNumber) {
        return switch (scenario) {
            case SUCCESS -> new Result(Outcome.CAPTURED, provider.p95LatencyMs, "Sandbox authorization captured.");
            case DECLINE -> new Result(Outcome.DECLINED, provider.p95LatencyMs, "Sandbox issuer decline.");
            case TIMEOUT -> new Result(Outcome.TIMEOUT, provider.p95LatencyMs + 1500, "Sandbox timeout; provider outcome is unknown until a webhook arrives.");
            case TRANSIENT_FAILURE -> attemptNumber == 1
                    ? new Result(Outcome.RETRYABLE_FAILURE, provider.p95LatencyMs + 40, "Sandbox provider unavailable; retryable.")
                    : new Result(Outcome.CAPTURED, provider.p95LatencyMs, "Captured by the next eligible sandbox provider.");
        };
    }
}
