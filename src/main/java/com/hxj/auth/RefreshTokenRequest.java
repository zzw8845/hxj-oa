package com.hxj.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @Schema(description = "刷新令牌（登录时下发，用于换取新的 Access Token）")
        @NotBlank(message = "refreshToken 不能为空") String refreshToken) {
}