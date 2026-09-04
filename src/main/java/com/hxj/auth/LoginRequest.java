package com.hxj.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求 DTO。
 *
 * <p>用于 {@code POST /api/auth/login} 接口，账号密码登录。
 */
public record LoginRequest(
        @Schema(description = "登录账号（用户名/工号）")
        @NotBlank(message = "请输入登录账号") String account,
        @Schema(description = "登录密码（明文传输，服务端 BCrypt 加密存储）")
        @NotBlank(message = "请输入登录密码") String password) {
}
