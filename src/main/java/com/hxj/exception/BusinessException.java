package com.hxj.exception;

import com.hxj.common.ErrorCode;
import lombok.Getter;

/**
 * 业务异常：Service/Controller 层抛出，由 {@link GlobalExceptionHandler} 统一捕获。
 *
 * <p>支持两种构造方式：
 * <ul>
 *   <li>{@link #BusinessException(ErrorCode)} — 使用 ErrorCode 的默认提示</li>
 *   <li>{@link #BusinessException(ErrorCode, String)} — 覆盖默认提示（场景化文案）</li>
 * </ul>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码 */
    private final ErrorCode errorCode;

    /** 使用 ErrorCode 默认提示 */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /** 覆盖默认提示（场景化文案） */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}