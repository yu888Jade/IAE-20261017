package com.english.assistant.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class EssayGenerationGuardService {
    private static final Duration LOCK_TTL = Duration.ofSeconds(90);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redis;
    private final ConcurrentHashMap<Long, AtomicBoolean> localLocks = new ConcurrentHashMap<>();

    public EssayGenerationGuardService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public LockHandle tryAcquire(Long userId) {
        AtomicBoolean local = localLocks.computeIfAbsent(userId, ignored -> new AtomicBoolean(false));
        if (!local.compareAndSet(false, true)) return null;

        String key = "essay_generation:user:" + userId;
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(key, token, LOCK_TTL);
            if (Boolean.FALSE.equals(acquired)) {
                releaseLocal(userId, local);
                return null;
            }
            return new LockHandle(userId, key, token, local, acquired != null);
        } catch (Exception ignored) {
            return new LockHandle(userId, key, token, local, false);
        }
    }

    public void release(LockHandle handle) {
        if (handle == null) return;
        try {
            if (handle.redisLocked()) {
                redis.execute(RELEASE_SCRIPT, Collections.singletonList(handle.key()), handle.token());
            }
        } catch (Exception ignored) {
        } finally {
            releaseLocal(handle.userId(), handle.local());
        }
    }

    private void releaseLocal(Long userId, AtomicBoolean local) {
        local.set(false);
        localLocks.remove(userId, local);
    }

    public record LockHandle(Long userId, String key, String token, AtomicBoolean local, boolean redisLocked) {}
}
