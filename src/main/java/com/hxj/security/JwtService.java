package com.hxj.security;

import com.hxj.entity.SysPermission;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 负责签发与解析 OA Access Token 与 Refresh Token。 */
@Service
public class JwtService {

    /** Refresh Token 有效期：7 天 */
    private static final long REFRESH_EXPIRATION_MS = 7 * 24 * 60 * 60 * 1_000L;
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN = "access";
    private static final String REFRESH_TOKEN = "refresh";

    private final SecretKey signingKey;
    private final long expirationMs;
    private final Clock clock;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs,
            Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.clock = clock;
    }

    public String issueAccessToken(SysUser user) {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .subject(user.getAccount())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN)
                .claim("userId", user.getId())
                .claim("account", user.getAccount())
                .claim("name", user.getName())
                .claim("department", user.getDepartment())
                .claim("post", user.getPost())
                .claim("roles", roleNames(user))
                .claim("permissions", permissionCodes(user))
                .claim("dataScopes", dataScopeCodes(user))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }

    /** 签发 Refresh Token：有效期 7 天，仅用于换取新 Access Token。 */
    public String issueRefreshToken(SysUser user) {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .subject(user.getAccount())
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN)
                .claim("userId", user.getId())
                .claim("account", user.getAccount())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusMillis(REFRESH_EXPIRATION_MS)))
                .signWith(signingKey)
                .compact();
    }

    /** 解析 Refresh Token，返回用户账号；非法或非 Refresh 类型抛出异常。 */
    public String parseRefreshToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!REFRESH_TOKEN.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new IllegalArgumentException("not a refresh token");
        }
        return claims.getSubject();
    }

    public Map<String, Object> parseClaims(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new LinkedHashMap<>(claims);
    }

    /** 获取 Token 过期时间，解析失败返回 null。 */
    public Date getExpiration(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getExpiration();
        } catch (Exception ex) {
            return null;
        }
    }

    public long getExpirationSeconds() {
        return expirationMs / 1_000;
    }

    public List<String> roleNames(SysUser user) {
        return user.getRoles().stream()
                .map(SysRole::getName)
                .sorted()
                .toList();
    }

    public List<String> permissionCodes(SysUser user) {
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(SysPermission::getCode)
                .distinct()
                .sorted()
                .toList();
    }

    public List<String> dataScopeCodes(SysUser user) {
        return user.getRoles().stream()
                .map(SysRole::getDataScope)
                .map(scope -> scope.getCode())
                .distinct()
                .sorted()
                .toList();
    }
}