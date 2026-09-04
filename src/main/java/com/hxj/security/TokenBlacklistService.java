package com.hxj.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Token 黑名单服务：将退出的 Token 写入 Redis，校验时检查。
 * 黑名单 TTL 与 Token 剩余有效期一致，不会无限增长。
 * Redis 不可用时自动降级为无操作（Token 校验仍正常通过）。
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

    private final Optional<StringRedisTemplate> redisTemplate;
    private final JwtService jwtService;
    private final boolean redisEnabled;

    public TokenBlacklistService(
            Optional<StringRedisTemplate> redisTemplate,
            JwtService jwtService,
            @Value("${spring.data.redis.enabled:true}") boolean redisEnabled) {
        this.redisTemplate = redisTemplate;
        this.jwtService = jwtService;
        this.redisEnabled = redisEnabled;
    }

    /** 将 Token 加入黑名单，TTL 为 Token 剩余有效期。 */
    public void blacklist(String token) {
        try {
            Date expiration = jwtService.getExpiration(token);
            if (expiration == null) {
                return;
            }
            Duration ttl = Duration.between(Instant.now(), expiration.toInstant());
            if (ttl.isNegative() || ttl.isZero()) {
                return;
            }
            if (redisEnabled && redisTemplate.isPresent()) {
                redisTemplate.get().opsForValue().set(BLACKLIST_PREFIX + token, "1", ttl);
                log.info("[logout] Token 已加入黑名单, ttl={}", ttl);
            }
        } catch (Exception ex) {
            log.warn("[logout] Token 加入黑名单失败: {}", ex.getMessage());
        }
    }

    /** 检查 Token 是否在黑名单中。 */
    public boolean isBlacklisted(String token) {
        if (!redisEnabled || redisTemplate.isEmpty()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.get().hasKey(BLACKLIST_PREFIX + token));
        } catch (Exception ex) {
            log.warn("[logout] 黑名单检查失败: {}", ex.getMessage());
            return false;
        }
    }
}
