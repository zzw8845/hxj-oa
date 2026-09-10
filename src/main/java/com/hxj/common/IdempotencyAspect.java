package com.hxj.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxj.exception.BusinessException;
import com.hxj.security.AuthenticatedUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 幂等性切面：防止网络重试导致重复提交/重复审批。
 *
 * <p><b>双模式幂等键</b>：
 * <ul>
 *   <li><b>前端显式 key</b>（推荐）：请求头 {@code Idempotency-Key}，客户端生成 UUID，
 *       精确控制幂等范围，缓存 10 分钟；</li>
 *   <li><b>自动 key</b>（前端零配合）：未携带请求头时，基于「用户 + 接口 + 参数摘要」自动生成，
 *       缓存 5 秒，仅覆盖网络重试场景，避免误伤相同参数的合法重复业务。</li>
 * </ul>
 *
 * <p><b>存储策略</b>：优先使用 Redis（跨实例共享），Redis 不可用时自动降级为内存实现。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>并发安全</b>：存储层原子占位，并发相同 key 的请求只有一个执行；</li>
 *   <li><b>用户隔离</b>：key 绑定当前登录用户，避免不同用户间 key 冲突；</li>
 *   <li><b>失败不缓存</b>：业务执行失败立即释放占位，允许客户端重试。</li>
 * </ul>
 */
@Aspect
@Component
public class IdempotencyAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyAspect.class);

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    /** 前端显式 key 的缓存时长：10 分钟 */
    private static final long EXPLICIT_TTL_MILLIS = 10 * 60 * 1_000L;
    /** 自动 key 的缓存时长：5 秒，仅覆盖网络重试窗口 */
    private static final long AUTO_TTL_MILLIS = 5 * 1_000L;

    private final ObjectMapper objectMapper;
    private final RedisIdempotencyStore redisStore;
    private final InMemoryIdempotencyStore memoryStore;

    public IdempotencyAspect(
            ObjectMapper objectMapper,
            RedisIdempotencyStore redisStore,
            InMemoryIdempotencyStore memoryStore) {
        this.objectMapper = objectMapper;
        this.redisStore = redisStore;
        this.memoryStore = memoryStore;
    }

    @Around("@annotation(com.hxj.common.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = currentRequest();
        String explicitKey = request == null ? null : request.getHeader(IDEMPOTENCY_KEY_HEADER);

        String cacheKey;
        long ttl;
        if (explicitKey != null && !explicitKey.isBlank()) {
            // 前端显式 key：精确幂等，长缓存
            cacheKey = resolveCacheKey(currentUser(), explicitKey);
            ttl = EXPLICIT_TTL_MILLIS;
        } else {
            // 前端零配合：自动 key（用户 + 接口 + 参数摘要），短缓存仅防网络重试
            cacheKey = resolveCacheKey(currentUser(), autoKey(joinPoint));
            ttl = AUTO_TTL_MILLIS;
        }

        // 原子占位：仅一个请求能获得执行权，其余直接返回（幂等拒绝）
        if (!tryAcquire(cacheKey, ttl)) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_REQUEST, "重复请求，请勿重复提交");
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            // 业务失败释放占位，允许客户端重试
            release(cacheKey);
            throw t;
        }
    }

    /** 优先 Redis，异常时降级内存。 */
    private boolean tryAcquire(String key, long ttl) {
        try {
            return redisStore.tryAcquire(key, ttl);
        } catch (Exception ex) {
            log.warn("[idempotency] Redis 不可用，降级为内存实现: {}", ex.getMessage());
            return memoryStore.tryAcquire(key, ttl);
        }
    }

    private void release(String key) {
        try {
            redisStore.release(key);
        } catch (Exception ex) {
            log.warn("[idempotency] Redis 释放失败，降级为内存释放: {}", ex.getMessage());
            memoryStore.release(key);
        }
    }

    /** 自动幂等键：方法签名 + 参数 JSON 摘要，同一用户相同请求内容生成相同 key。 */
    private String autoKey(ProceedingJoinPoint joinPoint) {
        try {
            String signature = joinPoint.getSignature().toShortString();
            String argsJson = objectMapper.writeValueAsString(joinPoint.getArgs());
            return "auto:" + signature + ":" + md5(argsJson);
        } catch (Exception ex) {
            // 序列化失败时退化为方法签名（粒度更粗，但仍有基本防重能力）
            return "auto:" + joinPoint.getSignature().toShortString();
        }
    }

    private String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    /** key 绑定当前登录用户，避免不同用户间 key 冲突。 */
    private String resolveCacheKey(String user, String key) {
        return user + ":" + key;
    }

    private String currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUserResponse u) {
            return u.account();
        }
        return "";
    }

    private HttpServletRequest currentRequest() {
        return Objects.isNull(RequestContextHolder.getRequestAttributes()) ? null
                : ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    }
}