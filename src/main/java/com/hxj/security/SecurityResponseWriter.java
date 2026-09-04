package com.hxj.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxj.common.ApiResponse;
import com.hxj.common.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Security 过滤器链阶段的统一 JSON 写出器。
 *
 * <p>Spring Security 的 {@code AuthenticationEntryPoint} 和 {@code AccessDeniedHandler}
 * 工作在过滤器链层，早于 Spring MVC，不经过 {@code @RestControllerAdvice}，
 * 因此需要本类手动写出 JSON 响应。
 *
 * <p>HTTP 状态码由调用方显式指定（401/403），响应体统一为 {@link ApiResponse} 格式。
 */
@Component
public class SecurityResponseWriter {

    private static final Logger log = LoggerFactory.getLogger(SecurityResponseWriter.class);

    private final ObjectMapper objectMapper;

    public SecurityResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 写出 401 响应 */
    public void writeUnauthorized(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, errorCode, message);
    }

    /** 写出 403 响应 */
    public void writeForbidden(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        write(response, HttpStatus.FORBIDDEN, errorCode, message);
    }

    /** 写出指定 HTTP 状态的 JSON 响应 */
    public void write(HttpServletResponse response, HttpStatus httpStatus, ErrorCode errorCode, String message) throws IOException {
        log.warn("[security] status={}, code={}, message={}", httpStatus.value(), errorCode.getCode(), message);
        response.setStatus(httpStatus.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.error(errorCode, message);
        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(body));
            writer.flush();
        }
    }
}