package com.hxj.common;

import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 统一响应增强器：在响应体写出前注入 requestId。
 *
 * <p>设计目的：requestId 注入逻辑此前散落在请求日志切面、全局异常处理器、
 * Security 401/403 处理器三处，属于复制粘贴式冗余。本类作为唯一收口点，
 * 覆盖 Controller 正常返回与 {@code @ExceptionHandler} 异常返回两类路径。
 *
 * <p>唯一例外：Security 过滤器链阶段的 401/403 直接写 Servlet 输出流，
 * 不经过 Spring MVC 消息转换器，由 {@code SecurityResponseWriter} 负责注入。
 */
@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body instanceof ApiResponse<?> apiResponse && apiResponse.requestId() == null) {
            String requestId = MDC.get(RequestIdFilter.MDC_REQUEST_ID);
            return new ApiResponse<>(
                    apiResponse.isSuccess(),
                    apiResponse.code(),
                    apiResponse.message(),
                    apiResponse.data(),
                    requestId);
        }
        return body;
    }
}
