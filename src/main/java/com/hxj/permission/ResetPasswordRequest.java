package com.hxj.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 管理员重置员工密码请求。 */
public record ResetPasswordRequest(
        @Schema(description = "新密码") @NotBlank String newPassword) {
}
