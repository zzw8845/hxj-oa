package com.hxj.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxj.auth.AuthException;
import com.hxj.common.ApiResponse;
import com.hxj.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GlobalExceptionHandler 单元测试：
 * 校验异常转换后的 ApiResponse 必须携带字符串业务码（code 字段）且 success 为 false。
 * 阿里风格：HTTP 状态码统一 200（AuthException 除外，返回 401），前端靠 success/code 分流。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("BusinessException 响应的 code 字段应为字符串业务码，success 为 false")
    void businessExceptionShouldCarryErrorCode() {
        ResponseEntity<ApiResponse<Void>> entity = handler.handleBusiness(
                new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "单据不存在或无权查看"));

        // 阿里风格：HTTP 统一 200
        assertThat(entity.getStatusCode().value()).isEqualTo(200);
        ApiResponse<Void> body = entity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("DOCUMENT_NOT_FOUND");
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.message()).isEqualTo("单据不存在或无权查看");
    }

    @Test
    @DisplayName("序列化后的 JSON 应包含 code 与 success 字段")
    void serializedJsonShouldContainCodeAndSuccess() throws Exception {
        ResponseEntity<ApiResponse<Void>> entity = handler.handleBusiness(
                new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "单据不存在或无权查看"));

        ApiResponse<Void> body = entity.getBody();
        assertThat(body).isNotNull();
        String json = objectMapper.writeValueAsString(body);
        assertThat(json).contains("\"code\":\"DOCUMENT_NOT_FOUND\"");
        assertThat(json).contains("\"success\":false");
    }

    @Test
    @DisplayName("AuthException 应携带认证错误码（code 字段），状态 401")
    void authExceptionShouldCarryErrorCode() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ResponseEntity<ApiResponse<Void>> entity = handler.handleAuth(
                new AuthException(ErrorCode.TOKEN_EXPIRED, "登录已过期，请重新登录"), response);

        // AuthException 返回 401
        assertThat(response.getStatus()).isEqualTo(401);
        ApiResponse<Void> body = entity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("TOKEN_EXPIRED");
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.message()).isEqualTo("登录已过期，请重新登录");
    }

    @Test
    @DisplayName("BusinessException 使用 ErrorCode 默认提示")
    void businessExceptionShouldUseDefaultMessage() {
        ResponseEntity<ApiResponse<Void>> entity = handler.handleBusiness(
                new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        ApiResponse<Void> body = entity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("DOCUMENT_NOT_FOUND");
        assertThat(body.message()).isEqualTo("单据不存在或无权查看");
        assertThat(body.isSuccess()).isFalse();
    }
}
