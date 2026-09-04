package com.hxj.security;

import com.hxj.common.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 将权限点不足统一转换为 JSON 403（业务码 ACCESS_DENIED）。 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityResponseWriter responseWriter;

    public JsonAccessDeniedHandler(SecurityResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        responseWriter.writeForbidden(response, ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.getDefaultMessage());
    }
}