package com.hxj.exception;

import com.hxj.auth.AuthException;
import com.hxj.common.ApiResponse;
import com.hxj.common.ErrorCodeEnum;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器（阿里 Java 开发手册风格）：
 * 将各类异常统一转为 {@link ApiResponse}，HTTP 状态码固定 200，
 * 前端靠 {@code success}/{@code code} 字段分流。
 *
 * <p>认证异常（{@link AuthException}）和 Security 过滤器链阶段的异常
 * 由 {@code JsonAuthenticationEntryPoint}/{@code JsonAccessDeniedHandler} 处理，
 * 不经过本处理器。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // —— 业务异常 ——

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCodeEnum errorCode = ex.getErrorCode();
        String message = ex.getMessage();
        log.warn("[business] code={}, message={}", errorCode.getCode(), message);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(errorCode, message));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthException ex, HttpServletResponse response) {
        ErrorCodeEnum errorCode = ex.getErrorCode();
        String message = ex.getMessage();
        log.warn("[auth] code={}, message={}", errorCode.getCode(), message);
        // AuthException 返回 401
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(errorCode, message));
    }

    // —— 参数校验 ——

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[validation] {}", detail);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(ErrorCodeEnum.VALIDATION_FAILED, detail));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[validation] {}", detail);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(ErrorCodeEnum.VALIDATION_FAILED, detail));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        String message = "缺少必要参数: " + ex.getParameterName();
        log.warn("[validation] {}", message);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(ErrorCodeEnum.VALIDATION_FAILED, message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "参数类型不正确: " + ex.getName();
        log.warn("[validation] {}", message);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(ErrorCodeEnum.ARGUMENT_TYPE_INVALID, message));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        String message = "缺少必要请求头: " + ex.getHeaderName();
        log.warn("[validation] {}", message);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(ErrorCodeEnum.MISSING_REQUEST_HEADER, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("[request] body 格式错误: {}", ex.getMessage());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(ErrorCodeEnum.REQUEST_BODY_INVALID));
    }

    /**
     * 缺少 multipart 部分（如上传附件时未带 file）。
     *
     * <p>不处理的话会落入 {@link #handleAll} 返回「系统繁忙，请稍后重试」，
     * 把客户端的调用错误伪装成服务端故障，既误导排查也无法给前端明确提示。
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex) {
        String message = "缺少必要的文件参数: " + ex.getRequestPartName();
        log.warn("[request] {}", message);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(ErrorCodeEnum.REQUEST_BODY_INVALID, message));
    }

    // —— Spring Security ——

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("[access] 权限不足");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(ErrorCodeEnum.ACCESS_DENIED));
    }

    // —— 兜底 ——

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(NoHandlerFoundException ex) {
        log.warn("[request] 路径不存在: {}", ex.getRequestURL());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(ErrorCodeEnum.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("[request] 方法不支持: {}", ex.getMessage());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(ErrorCodeEnum.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAll(Exception ex) {
        log.error("[system] 未捕获异常", ex);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(ErrorCodeEnum.INTERNAL_ERROR));
    }
}