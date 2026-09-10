package com.hxj.exception;

import com.hxj.common.ErrorCodeEnum;
import lombok.Getter;

/**
 * 业务异常：Service/Controller 层抛出，由 {@link GlobalExceptionHandler} 统一捕获。
 *
 * <p>支持两种构造方式：
 * <ul>
 *   <li>{@link #BusinessException(ErrorCodeEnum)} — 使用 ErrorCodeEnum 的默认提示</li>
 *   <li>{@link #BusinessException(ErrorCodeEnum, String)} — 覆盖默认提示（场景化文案）</li>
 * </ul>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码 */
    private final ErrorCodeEnum errorCode;

    /** 使用 ErrorCodeEnum 默认提示 */
    public BusinessException(ErrorCodeEnum errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /** 覆盖默认提示（场景化文案） */
    public BusinessException(ErrorCodeEnum errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}