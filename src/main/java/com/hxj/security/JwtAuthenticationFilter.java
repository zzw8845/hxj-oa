package com.hxj.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** 验证 Authorization Bearer JWT 并建立 Spring SecurityContext。 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final TokenBlacklistService blacklistService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            TokenBlacklistService blacklistService) {
        this.jwtService = jwtService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.blacklistService = blacklistService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = header.substring(7);
            if (blacklistService.isBlacklisted(token)) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(request, response, null);
                return;
            }
            Map<String, Object> claims = jwtService.parseClaims(token);
            AuthenticatedUserResponse principal = toPrincipal(claims);
            List<SimpleGrantedAuthority> authorities = principal.permissions().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, null);
        }
    }

    private AuthenticatedUserResponse toPrincipal(Map<String, Object> claims) {
        Object userIdValue = claims.get("userId");
        Long userId = userIdValue instanceof Number number ? number.longValue() : null;
        return new AuthenticatedUserResponse(
                userId,
                stringClaim(claims, "account"),
                stringClaim(claims, "name"),
                stringClaim(claims, "department"),
                stringClaim(claims, "post"),
                stringListClaim(claims, "roles"),
                stringListClaim(claims, "permissions"),
                stringListClaim(claims, "dataScopes"));
    }

    private String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value == null ? null : value.toString();
    }

    private List<String> stringListClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(Object::toString).toList();
    }
}