package com.hxj.exception;

import com.hxj.common.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GlobalExceptionHandler 异常分类 handler 测试：
 * 阿里风格：HTTP 状态码统一 200，前端靠 success/code 字段分流。
 */
class GlobalExceptionHandlerCategoryTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("请求体 JSON 格式错误应返回 code=REQUEST_BODY_INVALID，HTTP 200")
    void messageNotReadableShouldReturnRequestBodyInvalid() {
        ResponseEntity<ApiResponse<Void>> entity = handler.handleNotReadable(
                new HttpMessageNotReadableException("bad json", (org.springframework.http.HttpInputMessage) null));

        assertThat(entity.getStatusCode().value()).isEqualTo(200);
        ApiResponse<Void> body = entity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("REQUEST_BODY_INVALID");
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.message()).isEqualTo("请求体格式不正确");
    }

    @Test
    @DisplayName("路径不存在应返回 code=RESOURCE_NOT_FOUND，HTTP 200")
    void noHandlerShouldReturnResourceNotFound() {
        ResponseEntity<ApiResponse<Void>> entity = handler.handleNoHandler(
                new NoHandlerFoundException("GET", "unknown/path", null));

        assertThat(entity.getStatusCode().value()).isEqualTo(200);
        ApiResponse<Void> body = entity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.message()).isEqualTo("请求的资源不存在");
    }

    @Test
    @DisplayName("HTTP 方法不支持应返回 code=METHOD_NOT_ALLOWED，HTTP 200")
    void methodNotSupportedShouldReturnMethodNotAllowed() {
        ResponseEntity<ApiResponse<Void>> entity = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("DELETE"));

        assertThat(entity.getStatusCode().value()).isEqualTo(200);
        ApiResponse<Void> body = entity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.message()).isEqualTo("不支持的请求方法");
    }
}
