package com.hxj.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新增/编辑岗位请求。 */
public record SavePostRequest(
        @Schema(description = "岗位名称（唯一）") @NotBlank @Size(max = 100) String name) {
}
