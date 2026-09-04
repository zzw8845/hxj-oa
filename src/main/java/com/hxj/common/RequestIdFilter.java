package com.hxj.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * 链路追踪过滤器：在整个请求生命周期内维护 MDC 中的 requestId。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>最高优先级</b>：先于 Security 过滤器链执行，保证 401/403 响应也能携带 requestId；</li>
 *   <li><b>全局有效</b>：无论请求走到 Controller 还是抛异常进入 GlobalExceptionHandler，
 *       MDC 中的 requestId 都可用，错误响应与日志可关联；</li>
 *   <li><b>响应头回传</b>：将 requestId 写入 X-Request-ID 响应头，前端/网关可直接取用；</li>
 *   <li><b>复用上游 ID</b>：优先使用请求头 X-Request-ID（网关/上游传入），否则生成新 UUID。</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String MDC_REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = Optional.ofNullable(request.getHeader(REQUEST_ID_HEADER))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 请求结束清理 MDC，避免线程池串号
            MDC.remove(MDC_REQUEST_ID);
        }
    }
}
