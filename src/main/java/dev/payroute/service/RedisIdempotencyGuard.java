package dev.payroute.service;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class RedisIdempotencyGuard {
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private final StringRedisTemplate redis;
    public RedisIdempotencyGuard(StringRedisTemplate redis) { this.redis = redis; }

    public String acquire(String merchantId, String key) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(redisKey(merchantId, key), token, Duration.ofSeconds(15));
            return Boolean.TRUE.equals(acquired) ? token : null;
        } catch (DataAccessException unavailable) {
            // PostgreSQL's unique constraint remains authoritative when Redis is offline.
            return "redis-unavailable:" + token;
        }
    }
    public void release(String merchantId, String key, String token) {
        if (token == null || token.startsWith("redis-unavailable:")) return;
        try { redis.execute(RELEASE, List.of(redisKey(merchantId, key)), token); }
        catch (DataAccessException ignored) { /* lock expires; the database still owns correctness */ }
    }
    private String redisKey(String merchantId, String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((merchantId + "\u0000" + key).getBytes(StandardCharsets.UTF_8));
            return "payroute:idempotency:" + HexFormat.of().formatHex(digest);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
