package com.hxj.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiResponseAdvice 单元测试：
 * 校验 requestId 注入的唯一收口逻辑——MDC 有值时注入，无值时保持 null。
 */
class ApiResponseAdviceTest {

    private final ApiResponseAdvice advice = new ApiResponseAdvice();

    @AfterEach
    void tearDown() {
        MDC.remove(RequestIdFilter.MDC_REQUEST_ID);
    }

    @Test
    @DisplayName("MDC 中存在 requestId 时应注入 ApiResponse")
    void shouldInjectRequestIdFromMdc() {
        MDC.put(RequestIdFilter.MDC_REQUEST_ID, "req-123");
        ApiResponse<Void> response = ApiResponse.success();

        Object result = advice.beforeBodyWrite(response, null, null, null, null, null);

        assertThat(((ApiResponse<?>) result).requestId()).isEqualTo("req-123");
    }

    @Test
    @DisplayName("非 ApiResponse 类型的响应体应原样返回")
    void shouldPassThroughNonApiResponseBody() {
        String body = "raw";

        Object result = advice.beforeBodyWrite(body, null, null, null, null, null);

        assertThat(result).isSameAs(body);
    }
}
