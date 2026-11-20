package com.returnright.core;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed counters/flags for login lockout, rate limiting and JWT revocation (ADR-002).
 * Not on the money path: if Redis is unavailable we degrade to an in-process map rather than fail requests.
 */
@Component
public class KvStore {

    private static final Logger log = LoggerFactory.getLogger(KvStore.class);
    private final StringRedisTemplate redis;
    private final Map<String, long[]> memory = new ConcurrentHashMap<>(); // key -> [value, expiresAtMillis]

    public KvStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Increment a fixed-window counter; returns the new count. */
    public long incrementWindow(String key, Duration window) {
        try {
            Long v = redis.opsForValue().increment(key);
            if (v != null && v == 1L) {
                redis.expire(key, window);
            }
            return v == null ? 1 : v;
        } catch (RuntimeException e) {
            warn(e);
            long now = System.currentTimeMillis();
            long[] cur = memory.compute(key, (k, old) -> old == null || old[1] < now
                    ? new long[] {1, now + window.toMillis()}
                    : new long[] {old[0] + 1, old[1]});
            return cur[0];
        }
    }

    public long get(String key) {
        try {
            String v = redis.opsForValue().get(key);
            return v == null ? 0 : Long.parseLong(v);
        } catch (RuntimeException e) {
            warn(e);
            long[] cur = memory.get(key);
            return cur == null || cur[1] < System.currentTimeMillis() ? 0 : cur[0];
        }
    }

    public long ttlSeconds(String key) {
        try {
            Long ttl = redis.getExpire(key);
            return ttl == null || ttl < 0 ? 0 : ttl;
        } catch (RuntimeException e) {
            warn(e);
            long[] cur = memory.get(key);
            return cur == null ? 0 : Math.max(0, (cur[1] - System.currentTimeMillis()) / 1000);
        }
    }

    public void delete(String key) {
        memory.remove(key);
        try {
            redis.delete(key);
        } catch (RuntimeException e) {
            warn(e);
        }
    }

    public void setFlag(String key, Duration ttl) {
        memory.put(key, new long[] {1, System.currentTimeMillis() + ttl.toMillis()});
        try {
            redis.opsForValue().set(key, "1", ttl);
        } catch (RuntimeException e) {
            warn(e);
        }
    }

    public boolean hasFlag(String key) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(key));
        } catch (RuntimeException e) {
            warn(e);
            long[] cur = memory.get(key);
            return cur != null && cur[1] >= System.currentTimeMillis();
        }
    }

    public boolean ping() {
        try {
            return redis.getConnectionFactory() != null
                    && "PONG".equalsIgnoreCase(redis.getConnectionFactory().getConnection().ping());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private long lastWarn;

    private void warn(RuntimeException e) {
        long now = System.currentTimeMillis();
        if (now - lastWarn > 30_000) {
            lastWarn = now;
            log.warn("Redis unavailable, using in-memory fallback: {}", e.getMessage());
        }
    }
}
