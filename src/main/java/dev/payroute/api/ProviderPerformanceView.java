package dev.payroute.api;

public record ProviderPerformanceView(String providerCode, int attemptCount, int successCount,
                                     double observedSuccessRate, double averageLatencyMs) {}
