package dev.payroute.service;

import dev.payroute.domain.ProviderConfig;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class ProviderHealthCache {
    public record Snapshot(double successRate, long p95LatencyMs) {}
    private final StringRedisTemplate redis;
    public ProviderHealthCache(StringRedisTemplate redis) { this.redis = redis; }

    public Snapshot get(ProviderConfig provider) {
        try {
            String value = redis.opsForValue().get(key(provider.code));
            if (value != null) {
                String[] parts = value.split("\\|", -1);
                return new Snapshot(Double.parseDouble(parts[0]), Long.parseLong(parts[1]));
            }
        } catch (DataAccessException | NumberFormatException ignored) { }
        return new Snapshot(provider.successRate, provider.p95LatencyMs);
    }
    public void update(ProviderConfig provider) {
        try {
            redis.opsForValue().set(key(provider.code), provider.successRate + "|" + provider.p95LatencyMs, Duration.ofMinutes(10));
        } catch (DataAccessException ignored) { }
    }
    private String key(String code) { return "payroute:provider-health:" + code; }
}
