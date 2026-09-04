package com.hxj.auth;

import com.hxj.common.ErrorCode;
import lombok.Getter;

/**
 * 认证异常：登录失败、Token 过期、账号停用等场景抛出。
 *
 * <p>HTTP 响应状态码固定为 401，由 {@code JsonAuthenticationEntryPoint} 写出。
 *
 * <p>支持两种构造方式：
 * <ul>
 *   <li>{@link #AuthException(ErrorCode)} — 使用 ErrorCode 的默认提示</li>
 *   <li>{@link #AuthException(ErrorCode, String)} — 覆盖默认提示</li>
 * </ul>
 */
@Getter
public class AuthException extends RuntimeException {

    /** 业务错误码 */
    private final ErrorCode errorCode;

    /** 使用 ErrorCode 默认提示 */
    public AuthException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /** 覆盖默认提示 */
    public AuthException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}