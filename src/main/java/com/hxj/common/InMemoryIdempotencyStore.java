package com.hxj.common;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的幂等性存储：单实例部署或 Redis 不可用时的降级方案。
 *
 * <p>使用 {@code ConcurrentHashMap.putIfAbsent} 原子占位，占位有效期即幂等窗口。
 * 业务执行失败时调用 {@link #release} 删除占位，允许客户端重试。
 */
@Component
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final Map<String, Long> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, long ttlMillis) {
        long now = System.currentTimeMillis();
        Long existing = locks.get(key);
        if (existing != null && now - existing < ttlMillis) {
            return false;
        }
        // 原子占位：仅当 key 不存在时成功
        return locks.putIfAbsent(key, now) == null;
    }

    @Override
    public void release(String key) {
        locks.remove(key);
    }
}