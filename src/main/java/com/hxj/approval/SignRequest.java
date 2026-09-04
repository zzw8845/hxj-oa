package com.hxj.approval;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SignRequest(
        @Schema(description = "会签用户ID") @NotNull Long signUserId,
        @Schema(description = "会签意见") @NotBlank String reason) {
}