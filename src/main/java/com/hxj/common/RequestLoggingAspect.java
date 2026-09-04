package com.hxj.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;
import java.util.Optional;

/**
 * 请求日志切面：记录 Controller 层请求入参与响应结果、耗时。
 * <p>
 * requestId 的维护由 {@link RequestIdFilter} 负责，注入响应体由 {@link ApiResponseAdvice} 负责，
 * 本切面仅保留日志职责。
 */
@Aspect
@Component
public class RequestLoggingAspect {

    private final ObjectMapper objectMapper;

    public RequestLoggingAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Pointcut("execution(* com.hxj..*Controller.*(..))")
    public void controllerPoint() {
    }

    @Around("controllerPoint()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        logRequest(joinPoint);

        Object result = joinPoint.proceed();

        logResponse(joinPoint, result, startTime);
        return result;
    }

    private void logRequest(ProceedingJoinPoint joinPoint) {
        try {
            HttpServletRequest request = currentRequest();
            org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(joinPoint.getSignature().getDeclaringType());
            log.info("[requestId:{}] request => method:{}, args:{}, ip:{}",
                    MDC.get(RequestIdFilter.MDC_REQUEST_ID),
                    joinPoint.getSignature().toShortString(),
                    joinPoint.getArgs(),
                    Optional.ofNullable(request).map(r -> r.getRemoteAddr()).orElse(null));
        } catch (Exception ex) {
            // 日志失败不影响业务
        }
    }

    private void logResponse(ProceedingJoinPoint joinPoint, Object result, long startTime) {
        try {
            org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(joinPoint.getSignature().getDeclaringType());
            log.info("[requestId:{}] response => method:{}, result:{}, cost:{}ms",
                    MDC.get(RequestIdFilter.MDC_REQUEST_ID),
                    joinPoint.getSignature().toShortString(),
                    truncate(objectMapper.writeValueAsString(result)),
                    System.currentTimeMillis() - startTime);
        } catch (Exception ex) {
            // 日志失败不影响业务
        }
    }

    /** 响应体超长时截断，避免大分页查询打爆日志 */
    private String truncate(String payload) {
        return payload.length() <= 500 ? payload : payload.substring(0, 500) + "...(truncated)";
    }

    private HttpServletRequest currentRequest() {
        return Objects.isNull(RequestContextHolder.getRequestAttributes()) ? null
                : ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    }
}
