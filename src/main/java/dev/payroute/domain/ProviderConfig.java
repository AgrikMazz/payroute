package dev.payroute.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "providers")
public class ProviderConfig {
    @Id @Column(length = 40) public String code;
    @Column(name = "display_name", nullable = false, length = 100) public String displayName;
    @Column(nullable = false) public boolean enabled;
    @Column(name = "supported_methods", nullable = false, length = 120) public String supportedMethods;
    @Column(name = "provider_fee_bps", nullable = false) public int providerFeeBps;
    @Column(name = "fixed_fee_minor", nullable = false) public long fixedFeeMinor;
    @Column(name = "success_rate", nullable = false) public double successRate;
    @Column(name = "p95_latency_ms", nullable = false) public long p95LatencyMs;
    public ProviderConfig() {}
}
