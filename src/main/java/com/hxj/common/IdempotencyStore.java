package com.hxj.common;

/**
 * 幂等性存储抽象：负责幂等键的原子占位与释放。
 *
 * <p>实现：
 * <ul>
 *   <li>{@link RedisIdempotencyStore}：基于 Redis SETNX，跨实例共享，生产环境使用；</li>
 *   <li>{@link InMemoryIdempotencyStore}：基于 ConcurrentHashMap，单实例/测试/Redis 不可用时降级。</li>
 * </ul>
 */
public interface IdempotencyStore {

    /**
     * 原子占位：尝试获取指定 key 的执行权。
     *
     * @param key 幂等键（已绑定用户）
     * @param ttlMillis 占位有效期（毫秒）
     * @return true 表示本线程获得执行权；false 表示已有请求在处理
     */
    boolean tryAcquire(String key, long ttlMillis);

    /**
     * 释放占位：业务执行失败时调用，允许后续重试。
     *
     * @param key 幂等键
     */
    void release(String key);
}