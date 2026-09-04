package com.hxj.common;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的幂等性存储：使用 SETNX 原子占位，跨实例共享。
 *
 * <p>Key 设计：{@code oa:idempotency:{key}}，占位有效期即幂等窗口。
 * 业务执行失败时调用 {@link #release} 删除 key，允许客户端重试。
 */
@Component
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String KEY_PREFIX = "oa:idempotency:";

    private final StringRedisTemplate redisTemplate;

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquire(String key, long ttlMillis) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + key, "1", Duration.ofMillis(ttlMillis));
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void release(String key) {
        redisTemplate.delete(KEY_PREFIX + key);
    }
}