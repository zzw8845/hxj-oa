package com.hxj.common;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * 统一 API 响应包装 (阿里巴巴 Java 开发手册风格)。
 *
 * <p>字段语义分工：
 * <ul>
 *   <li>{@code success}：成功标志，true 表示成功，false 表示失败；</li>
 *   <li>{@code code}：业务码，成功时为 {@code "SUCCESS"}，失败时为具体错误码（如 {@code "DOCUMENT_NOT_FOUND"}）；</li>
 *   <li>{@code message}：面向用户的中文提示，成功时为 null；</li>
 *   <li>{@code data}：业务数据，失败时为 null；</li>
 *   <li>{@code requestId}：链路追踪 ID。</li>
 * </ul>
 *
 * <p>注意：record 组件命名为 {@code ok}（而非 {@code success}），
 * 否则会与静态工厂方法 {@link #success()} 造成名称冲突。
 * 通过 {@code @JsonProperty("success")} 保持 JSON 输出字段名不变。
 *
 * @param ok        成功标志
 * @param code      业务码
 * @param message   错误提示
 * @param data      返回数据
 * @param requestId 链路追踪 ID
 * @param <T>       数据泛型
 */
public record ApiResponse<T>(
        @JsonProperty("success") boolean ok,
        String code,
        String message,
        T data,
        String requestId) implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功标志访问器（兼容旧代码 body.isSuccess() 调用） */
    public boolean isSuccess() {
        return ok;
    }

    /** 成功响应（有数据） */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "SUCCESS", null, data, null);
    }

    /** 成功响应（无数据） */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(true, "SUCCESS", null, null, null);
    }

    /** 错误响应：使用 ErrorCode 默认提示 */
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getDefaultMessage(), null, null);
    }

    /** 错误响应：覆盖 ErrorCode 默认提示 */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.getCode(), message, null, null);
    }
}
