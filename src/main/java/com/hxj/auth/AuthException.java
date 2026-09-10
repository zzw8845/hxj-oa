package com.hxj.auth;

import com.hxj.common.ErrorCodeEnum;
import lombok.Getter;

/**
 * 认证异常：登录失败、Token 过期、账号停用等场景抛出。
 *
 * <p>HTTP 响应状态码固定为 401，由 {@code JsonAuthenticationEntryPoint} 写出。
 *
 * <p>支持两种构造方式：
 * <ul>
 *   <li>{@link #AuthException(ErrorCodeEnum)} — 使用 ErrorCodeEnum 的默认提示</li>
 *   <li>{@link #AuthException(ErrorCodeEnum, String)} — 覆盖默认提示</li>
 * </ul>
 */
@Getter
public class AuthException extends RuntimeException {

    /** 业务错误码 */
    private final ErrorCodeEnum errorCode;

    /** 使用 ErrorCodeEnum 默认提示 */
    public AuthException(ErrorCodeEnum errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /** 覆盖默认提示 */
    public AuthException(ErrorCodeEnum errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}