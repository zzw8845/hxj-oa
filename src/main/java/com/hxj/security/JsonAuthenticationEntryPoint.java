package com.hxj.security;

import com.hxj.common.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 将匿名或无效 JWT 请求统一转换为 JSON 401（业务码 AUTH_FAILED）。 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityResponseWriter responseWriter;

    public JsonAuthenticationEntryPoint(SecurityResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        responseWriter.writeUnauthorized(response, ErrorCode.AUTH_FAILED, ErrorCode.AUTH_FAILED.getDefaultMessage());
    }
}