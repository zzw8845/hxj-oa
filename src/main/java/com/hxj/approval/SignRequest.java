package com.hxj.approval;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SignRequest(
        @Schema(description = "会签用户账号（与 EmployeeResponse.account 同源）") @NotBlank String signUserAccount,
        @Schema(description = "会签意见") @NotBlank String reason) {
}