package com.hxj.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 离职交接批量转交请求。 */
public record OffboardingTransferRequest(
        @Schema(description = "交接人登录账号（须在职且非离职员工本人）") @NotBlank String transferToAccount) {
}
